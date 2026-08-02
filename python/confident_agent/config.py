import os

CONFIDENT_API_KEY = os.environ.get("CONFIDENT_API_KEY", "")
CONFIDENT_WS_BASE_URL = (
    os.environ.get("CONFIDENT_WS_BASE_URL", "")
    or "wss://deepeval.confident-ai.com/ws/relay"
)
CONFIDENT_HANDLER = os.environ.get("CONFIDENT_HANDLER", "")

HEARTBEAT_INTERVAL_S = 45
INITIAL_RECONNECT_DELAY_S = 1
MAX_RECONNECT_DELAY_S = 45
