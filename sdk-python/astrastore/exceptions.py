class AstraError(Exception):
    """Base exception for all AstraStore SDK errors."""
    def __init__(self, message: str, status_code: int = 0):
        super().__init__(message)
        self.status_code = status_code


class AstraAuthError(AstraError):
    """Raised when authentication or authorization fails (HTTP 401/403)."""
    def __init__(self, message: str, status_code: int = 401):
        super().__init__(message, status_code)


class AstraNotFoundError(AstraError):
    """Raised when a bucket, object, or key is not found (HTTP 404)."""
    def __init__(self, message: str, status_code: int = 404):
        super().__init__(message, status_code)


class AstraValidationError(AstraError):
    """Raised when request payload or parameters are invalid (HTTP 400/409)."""
    def __init__(self, message: str, status_code: int = 400):
        super().__init__(message, status_code)


class AstraServerError(AstraError):
    """Raised when AstraStore server returns an internal error (HTTP 5xx)."""
    def __init__(self, message: str, status_code: int = 500):
        super().__init__(message, status_code)
