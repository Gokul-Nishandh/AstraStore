from dataclasses import dataclass
from typing import Optional, List, Set


@dataclass
class Bucket:
    id: str
    name: str
    ownerId: Optional[str] = None
    createdAt: Optional[str] = None


@dataclass
class ObjectRecord:
    id: str
    bucketId: str
    key: str
    sizeBytes: int
    checksum: str
    contentType: Optional[str] = None
    status: Optional[str] = None
    createdAt: Optional[str] = None
    chunksReplicated: Optional[int] = 0
    chunksTotal: Optional[int] = 0


@dataclass
class UploadResult:
    objectId: str
    bucketId: str
    key: str
    sizeBytes: int
    checksum: str
    chunkCount: int
    status: str


@dataclass
class AuthResponse:
    token: str
    type: str
    refreshToken: Optional[str] = None
    userId: Optional[str] = None
    username: Optional[str] = None
    email: Optional[str] = None
    roles: Optional[List[str]] = None


@dataclass
class ApiKey:
    id: int
    name: str
    key: Optional[str] = None
    keyPrefix: Optional[str] = None
    expiresAt: Optional[str] = None
    createdAt: Optional[str] = None
