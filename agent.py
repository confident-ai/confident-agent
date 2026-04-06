import asyncio
import json
import logging

import aiohttp
from pydantic import ValidationError

from config import (
    CONFIDENT_API_KEY,
    CONFIDENT_WS_BASE_URL,
    HEARTBEAT_INTERVAL_S,
    INITIAL_RECONNECT_DELAY_S,
    MAX_RECONNECT_DELAY_S,
)
from schemas import RelayRequest, RelayResponse, RelayResponseType, ResponseMode

logger = logging.getLogger("relay-agent")


class RelayAgent:
    def __init__(
        self,
        server_url: str,
        api_key: str,
    ):
        self.server_url = server_url
        self.api_key = api_key
        self._reconnect_delay = INITIAL_RECONNECT_DELAY_S
        self._running = True

    async def start(self):
        while self._running:
            try:
                await self._connect()
            except Exception as e:
                logger.error(f"connection error: {e}")
            if not self._running:
                break
            logger.info(f"reconnecting in {self._reconnect_delay}s")
            await asyncio.sleep(self._reconnect_delay)
            self._reconnect_delay = min(
                self._reconnect_delay * 2, MAX_RECONNECT_DELAY_S
            )

    async def _connect(self):
        headers = {
            "Authorization": f"Bearer {self.api_key}"
        }
        async with aiohttp.ClientSession() as session:
            async with session.ws_connect(
                self.server_url, 
                headers=headers, 
                heartbeat=HEARTBEAT_INTERVAL_S
            ) as ws:
                logger.info(f"connected to {self.server_url}")
                self._reconnect_delay = INITIAL_RECONNECT_DELAY_S
                await self._listen(ws, session)

    async def _listen(
        self, 
        ws: aiohttp.ClientWebSocketResponse, 
        session: aiohttp.ClientSession
    ):
        async for msg in ws:
            if msg.type == aiohttp.WSMsgType.TEXT:
                asyncio.create_task(self._handle_request(ws, session, msg.data))
            elif msg.type in (
                aiohttp.WSMsgType.CLOSED,
                aiohttp.WSMsgType.ERROR,
            ):
                logger.info(f"WebSocket closed or errored: msg.type={msg.type}, msg={msg}")
                break

    async def _handle_request(
        self,
        ws: aiohttp.ClientWebSocketResponse,
        session: aiohttp.ClientSession,
        raw: str,
    ):
        try:
            request = RelayRequest.model_validate_json(raw)
        except ValidationError as e:
            logger.error(f"failed to parse relay request: {e}")
            return

        try:
            timeout = aiohttp.ClientTimeout(total=request.timeout)
            kwargs: dict = {
                "method": request.method,
                "url": request.url,
                "headers": request.headers,
                "timeout": timeout,
            }
            if request.method.upper() != "GET" and request.body is not None:
                kwargs["json"] = request.body

            logger.info(f"[relay] {request.method} {request.url} (request {request.id})")
            async with session.request(**kwargs) as response:
                is_streaming = request.response_mode in (
                    ResponseMode.SSE_STREAMING,
                    ResponseMode.HTTP_STREAMING,
                )

                if is_streaming:
                    # Send response headers first
                    header_msg = RelayResponse(
                        id=request.id,
                        type=RelayResponseType.CHUNK,
                        statusCode=response.status,
                        headers=dict(response.headers),
                        body="",
                    )
                    await ws.send_json(header_msg.model_dump())

                    # Stream chunks as they arrive
                    async for raw_chunk in response.content.iter_any():
                        text = raw_chunk.decode("utf-8", errors="replace")
                        if not text:
                            continue
                        chunk_msg = RelayResponse(
                            id=request.id,
                            type=RelayResponseType.CHUNK,
                            statusCode=response.status,
                            headers={},
                            body=text,
                        )
                        await ws.send_json(chunk_msg.model_dump())

                    # Signal stream end
                    end_msg = RelayResponse(
                        id=request.id,
                        type=RelayResponseType.STREAM_END,
                        statusCode=response.status,
                        headers={},
                        body="",
                    )
                    await ws.send_json(end_msg.model_dump())
                    logger.info(f"[relay] {request.method} {request.url} : {response.status} streamed (request {request.id})")
                else:
                    response_body = await response.text()
                    logger.info(f"[relay] {request.method} {request.url} : {response.status} (request {request.id})")

                    relay_response = RelayResponse(
                        id=request.id,
                        type=RelayResponseType.RESPONSE,
                        statusCode=response.status,
                        headers=dict(response.headers),
                        body=response_body,
                    )
                    await ws.send_json(relay_response.model_dump())
        except Exception as e:
            logger.error(f"request {request.id} failed: {e}")
            error_response = RelayResponse(
                id=request.id,
                type=RelayResponseType.ERROR,
                statusCode=502,
                headers={},
                body=json.dumps({"error": str(e)}),
            )
            await ws.send_json(error_response.model_dump())

    def stop(self):
        self._running = False


def create_agent() -> RelayAgent:
    if not CONFIDENT_API_KEY:
        raise ValueError("CONFIDENT_API_KEY is required")
    if not CONFIDENT_WS_BASE_URL:
        raise ValueError("CONFIDENT_WS_BASE_URL is required")

    return RelayAgent(
        server_url=CONFIDENT_WS_BASE_URL,
        api_key=CONFIDENT_API_KEY,
    )
