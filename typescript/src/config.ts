export const CONFIDENT_API_KEY = process.env.CONFIDENT_API_KEY ?? "";
export const CONFIDENT_WS_BASE_URL =
  process.env.CONFIDENT_WS_BASE_URL || "wss://deepeval.confident-ai.com/ws/relay";
export const CONFIDENT_HANDLER = process.env.CONFIDENT_HANDLER ?? "";

export const HEARTBEAT_INTERVAL_S = 45;
export const INITIAL_RECONNECT_DELAY_S = 1;
export const MAX_RECONNECT_DELAY_S = 45;
