import asyncio
import inspect
import json
import logging
from typing import Any, Callable

from ..schemas import RelayRequest, RelayResponse, RelayResponseType, Request, Response

logger = logging.getLogger("relay-agent")


async def execute_handler(fn: Callable, request: RelayRequest) -> RelayResponse:
    try:
        body = request.body if isinstance(request.body, dict) else {}
        handler_request = Request.model_validate(body)

        if inspect.iscoroutinefunction(fn):
            result = await asyncio.wait_for(
                fn(handler_request), timeout=request.timeout
            )
        else:
            result = await asyncio.wait_for(
                asyncio.to_thread(fn, handler_request), timeout=request.timeout
            )

        response = _normalize(result)
        return RelayResponse(
            id=request.id,
            type=RelayResponseType.RESPONSE,
            statusCode=200,
            headers={"Content-Type": "application/json"},
            body=response.model_dump_json(),
        )
    except asyncio.TimeoutError:
        logger.error(
            f"handler timed out after {request.timeout}s (request {request.id})"
        )
        return _error_response(
            request.id, f"handler timed out after {request.timeout}s"
        )
    except Exception as e:
        logger.error(f"handler raised for request {request.id}: {e}")
        return _error_response(request.id, str(e) or type(e).__name__)


def _normalize(result: Any) -> Response:
    if isinstance(result, Response):
        return result
    if isinstance(result, str):
        return Response(output=result)
    if result is None:
        raise ValueError("handler returned None — return a string or a Response")
    raise ValueError(
        f"handler returned {type(result).__name__} — return a string or a Response"
    )


def _error_response(request_id: str, message: str) -> RelayResponse:
    return RelayResponse(
        id=request_id,
        type=RelayResponseType.ERROR,
        statusCode=500,
        headers={"Content-Type": "application/json"},
        body=json.dumps({"error": message}),
    )
