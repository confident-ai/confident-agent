use std::env;

pub const HEARTBEAT_INTERVAL_S: u64 = 45;
pub const INITIAL_RECONNECT_DELAY_S: u64 = 1;
pub const MAX_RECONNECT_DELAY_S: u64 = 45;

pub fn confident_api_key() -> String {
    env::var("CONFIDENT_API_KEY").unwrap_or_default()
}

pub fn confident_ws_base_url() -> String {
    let url = env::var("CONFIDENT_WS_BASE_URL").unwrap_or_default();
    if url.is_empty() {
        "wss://deepeval.confident-ai.com/ws/relay".to_string()
    } else {
        url
    }
}
