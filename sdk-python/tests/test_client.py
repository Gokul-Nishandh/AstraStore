import unittest
from astrastore.client import AstraClient
from astrastore.models import Bucket, UploadResult
from astrastore.exceptions import AstraAuthError, AstraNotFoundError


class TestAstraClient(unittest.TestCase):
    def test_client_init_defaults(self):
        client = AstraClient(base_url="http://localhost:8080", api_key="test-key")
        self.assertEqual(client.base_url, "http://localhost:8080")
        self.assertEqual(client.api_key, "test-key")
        self.assertEqual(client.access_token, "test-key")

    def test_headers_generation(self):
        client = AstraClient(base_url="http://localhost:8080", api_key="my-secret-key")
        headers = client._get_headers()
        self.assertEqual(headers["Content-Type"], "application/json")
        self.assertEqual(headers["X-API-Key"], "my-secret-key")
        self.assertEqual(headers["Authorization"], "Bearer my-secret-key")

    def test_bucket_model(self):
        b = Bucket(id="b-123", name="my-bucket", ownerId="owner-1")
        self.assertEqual(b.id, "b-123")
        self.assertEqual(b.name, "my-bucket")


if __name__ == "__main__":
    unittest.main()
