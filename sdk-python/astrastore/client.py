import json
import os
import urllib.request
import urllib.error
from typing import Optional, List, BinaryIO, Union, Generator
from .models import Bucket, ObjectRecord, UploadResult, AuthResponse, ApiKey
from .exceptions import (
    AstraError,
    AstraAuthError,
    AstraNotFoundError,
    AstraValidationError,
    AstraServerError,
)


class AstraClient:
    """Python Client SDK for AstraStore Distributed Storage System."""

    def __init__(
        self,
        base_url: str = "http://localhost:8080",
        api_key: Optional[str] = None,
        email: Optional[str] = None,
        password: Optional[str] = None,
        timeout: float = 30.0,
    ):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.email = email
        self.password = password
        self.timeout = timeout
        self.access_token: Optional[str] = api_key
        self.refresh_token: Optional[str] = None

        if email and password and not api_key:
            self.login(email, password)

    def _get_headers(self, content_type: Optional[str] = "application/json") -> dict:
        headers = {}
        if content_type:
            headers["Content-Type"] = content_type

        token = self._get_valid_token()
        if token:
            if self.api_key:
                headers["X-API-Key"] = token
                headers["Authorization"] = f"Bearer {token}"
            else:
                headers["Authorization"] = f"Bearer {token}"
        return headers

    def _get_valid_token(self) -> Optional[str]:
        if self.access_token:
            return self.access_token
        if self.refresh_token:
            try:
                self.refresh()
                return self.access_token
            except Exception:
                pass
        if self.email and self.password:
            self.login(self.email, self.password)
            return self.access_token
        return None

    def _request(
        self,
        method: str,
        path: str,
        data: Optional[Union[bytes, str, BinaryIO]] = None,
        headers: Optional[dict] = None,
    ) -> tuple[int, dict, bytes]:
        url = f"{self.base_url}{path}"
        req_headers = headers if headers is not None else self._get_headers()

        req = urllib.request.Request(url, data=data, headers=req_headers, method=method)

        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as response:
                status_code = response.status
                resp_headers = dict(response.headers)
                body = response.read()
                return status_code, resp_headers, body
        except urllib.error.HTTPError as e:
            body = e.read()
            self._handle_error(e.code, body.decode("utf-8", errors="ignore"))
        except urllib.error.URLError as e:
            raise AstraError(f"Network error connecting to AstraStore: {e.reason}")

    def _handle_error(self, status_code: int, body_str: str):
        msg = f"AstraStore request failed [HTTP {status_code}]: {body_str}"
        if status_code in (401, 403):
            raise AstraAuthError(msg, status_code)
        elif status_code == 404:
            raise AstraNotFoundError(msg, status_code)
        elif status_code in (400, 409):
            raise AstraValidationError(msg, status_code)
        else:
            raise AstraServerError(msg, status_code)

    def login(self, email: str, password: str) -> AuthResponse:
        payload = json.dumps({"email": email, "password": password}).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        _, _, body = self._request("POST", "/api/auth/login", data=payload, headers=headers)
        data = json.loads(body.decode("utf-8"))
        auth_resp = AuthResponse(**data)
        self.access_token = auth_resp.token
        self.refresh_token = auth_resp.refreshToken
        return auth_resp

    def refresh(self) -> AuthResponse:
        if not self.refresh_token:
            raise AstraAuthError("No refresh token available")
        payload = json.dumps({"refreshToken": self.refresh_token}).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        _, _, body = self._request("POST", "/api/auth/refresh", data=payload, headers=headers)
        data = json.loads(body.decode("utf-8"))
        auth_resp = AuthResponse(**data)
        self.access_token = auth_resp.token
        self.refresh_token = auth_resp.refreshToken
        return auth_resp

    # =========================================
    # Bucket Operations
    # =========================================

    def create_bucket(self, name: str) -> Bucket:
        payload = json.dumps({"name": name}).encode("utf-8")
        _, _, body = self._request("POST", "/api/v1/buckets", data=payload)
        data = json.loads(body.decode("utf-8"))
        return Bucket(**data)

    def get_bucket(self, bucket_id: str) -> Bucket:
        _, _, body = self._request("GET", f"/api/v1/buckets/{bucket_id}")
        data = json.loads(body.decode("utf-8"))
        return Bucket(**data)

    def list_buckets() -> List[Bucket]:
        _, _, body = self._request("GET", "/api/v1/buckets")
        res = json.loads(body.decode("utf-8"))
        items = res.get("content", res) if isinstance(res, dict) else res
        return [Bucket(**item) for item in items]

    def delete_bucket(self, bucket_id: str):
        self._request("DELETE", f"/api/v1/buckets/{bucket_id}")

    # =========================================
    # Object Streaming Upload & Download
    # =========================================

    def upload_object(
        self,
        bucket_id: str,
        key: str,
        data: Union[bytes, str, BinaryIO],
        content_type: str = "application/octet-stream",
    ) -> UploadResult:
        sanitized_key = key.lstrip("/")
        path = f"/api/v1/buckets/{bucket_id}/objects/{sanitized_key}"
        headers = self._get_headers(content_type=content_type)

        if isinstance(data, str):
            if os.path.exists(data):
                with open(data, "rb") as f:
                    payload = f.read()
            else:
                payload = data.encode("utf-8")
        elif hasattr(data, "read"):
            payload = data.read()
        else:
            payload = data

        _, _, body = self._request("PUT", path, data=payload, headers=headers)
        res = json.loads(body.decode("utf-8"))
        return UploadResult(**res)

    def download_object(self, bucket_id: str, key: str, output_path_or_stream: Union[str, BinaryIO]):
        sanitized_key = key.lstrip("/")
        path = f"/api/v1/buckets/{bucket_id}/objects/{sanitized_key}"
        headers = self._get_headers(content_type=None)

        _, _, body = self._request("GET", path, headers=headers)
        if isinstance(output_path_or_stream, str):
            with open(output_path_or_stream, "wb") as f:
                f.write(body)
        elif hasattr(output_path_or_stream, "write"):
            output_path_or_stream.write(body)

    def download_object_bytes(self, bucket_id: str, key: str) -> bytes:
        sanitized_key = key.lstrip("/")
        path = f"/api/v1/buckets/{bucket_id}/objects/{sanitized_key}"
        headers = self._get_headers(content_type=None)
        _, _, body = self._request("GET", path, headers=headers)
        return body

    def get_object_metadata(self, object_id: str) -> ObjectRecord:
        _, _, body = self._request("GET", f"/api/v1/objects/{object_id}")
        data = json.loads(body.decode("utf-8"))
        return ObjectRecord(**data)

    def delete_object(self, object_id: str):
        self._request("DELETE", f"/api/v1/objects/{object_id}")

    # =========================================
    # API Key Operations
    # =========================================

    def create_api_key(self, name: str) -> ApiKey:
        payload = json.dumps({"name": name}).encode("utf-8")
        _, _, body = self._request("POST", "/api/auth/keys", data=payload)
        data = json.loads(body.decode("utf-8"))
        return ApiKey(**data)

    def list_api_keys() -> List[ApiKey]:
        _, _, body = self._request("GET", "/api/auth/keys")
        items = json.loads(body.decode("utf-8"))
        return [ApiKey(**item) for item in items]

    def revoke_api_key(self, key_id: int):
        self._request("DELETE", f"/api/auth/keys/{key_id}")
