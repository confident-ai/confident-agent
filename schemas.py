from enum import Enum
from typing import Any, Dict, Optional

from pydantic import BaseModel


class ResponseMode(str, Enum):
    HTTP_RESPONSE = "HTTP_RESPONSE"
    SSE_STREAMING = "SSE_STREAMING"
    HTTP_STREAMING = "HTTP_STREAMING"


class RelayRequest(BaseModel):
    id: str
    method: str
    url: str
    headers: Dict[str, str] = {}
    body: Optional[Any] = None
    timeout: int = 60
    response_mode: ResponseMode = ResponseMode.HTTP_RESPONSE


class RelayResponseType(str, Enum):
    RESPONSE = "response"
    ERROR = "error"
    CHUNK = "chunk"
    STREAM_END = "stream_end"


class RelayResponse(BaseModel):
    id: str
    type: RelayResponseType
    statusCode: int
    headers: Dict[str, str] = {}
    body: str
