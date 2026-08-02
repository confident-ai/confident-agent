from enum import Enum
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, ConfigDict


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


class ToolCall(BaseModel):
    model_config = ConfigDict(extra="allow")

    name: str
    description: Optional[str] = None
    input_parameters: Optional[Dict[str, Any]] = None
    output: Optional[Any] = None


class Turn(BaseModel):
    model_config = ConfigDict(extra="allow")

    role: str
    content: str


class Request(BaseModel):
    model_config = ConfigDict(extra="allow")

    input: str = ""
    context: List[str] = []
    retrieval_context: List[str] = []
    expected_output: Optional[str] = None
    turns: List[Turn] = []
    scenario: Optional[str] = None
    state: Any = None
    prompts: Optional[Dict[str, Any]] = None
    hyperparameters: Optional[Dict[str, Any]] = None
    test_case_id: Optional[str] = None
    turn_id: Optional[str] = None


class Response(BaseModel):
    output: str
    retrieval_context: Optional[List[str]] = None
    tools_called: Optional[List[ToolCall]] = None
    state: Any = None
