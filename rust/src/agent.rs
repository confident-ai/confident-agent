use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::time::Duration;

use futures_util::{SinkExt, StreamExt};
use tokio::net::TcpStream;
use tokio::sync::mpsc::{self, UnboundedSender};
use tokio_tungstenite::tungstenite::client::IntoClientRequest;
use tokio_tungstenite::tungstenite::Message;
use tokio_tungstenite::{MaybeTlsStream, WebSocketStream};

use crate::config::{
    confident_api_key, confident_ws_base_url, HEARTBEAT_INTERVAL_S, INITIAL_RECONNECT_DELAY_S,
    MAX_RECONNECT_DELAY_S,
};
use crate::handler::{execute_handler, registered_handlers, HandlerFn};
use crate::schemas::{RelayRequest, RelayResponse, RelayResponseType, ResponseMode};

type Ws = WebSocketStream<MaybeTlsStream<TcpStream>>;

pub struct RelayAgent {
    server_url: String,
    api_key: String,
    handler_fn: Option<HandlerFn>,
    reconnect_delay: AtomicU64,
    running: AtomicBool,
}

impl RelayAgent {
    pub fn new(server_url: String, api_key: String, handler_fn: Option<HandlerFn>) -> Self {
        RelayAgent {
            server_url,
            api_key,
            handler_fn,
            reconnect_delay: AtomicU64::new(INITIAL_RECONNECT_DELAY_S),
            running: AtomicBool::new(true),
        }
    }

    pub async fn start(&self) {
        while self.running.load(Ordering::SeqCst) {
            if let Err(e) = self.connect().await {
                log::error!("connection error: {e}");
            }
            if !self.running.load(Ordering::SeqCst) {
                break;
            }
            let delay = self.reconnect_delay.load(Ordering::SeqCst);
            log::info!("reconnecting in {delay}s");
            tokio::time::sleep(Duration::from_secs(delay)).await;
            self.reconnect_delay
                .store((delay * 2).min(MAX_RECONNECT_DELAY_S), Ordering::SeqCst);
        }
    }

    async fn connect(&self) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
        let mut request = self.server_url.as_str().into_client_request()?;
        request
            .headers_mut()
            .insert("Authorization", format!("Bearer {}", self.api_key).parse()?);
        let (ws, _) = tokio_tungstenite::connect_async(request).await?;
        log::info!("connected to {}", self.server_url);
        self.reconnect_delay
            .store(INITIAL_RECONNECT_DELAY_S, Ordering::SeqCst);
        self.listen(ws).await;
        Ok(())
    }

    async fn listen(&self, ws: Ws) {
        let (mut write, mut read) = ws.split();
        let (tx, mut rx) = mpsc::unbounded_channel::<Message>();
        let client = reqwest::Client::new();

        tokio::spawn(async move {
            let heartbeat_period = Duration::from_secs(HEARTBEAT_INTERVAL_S);
            let mut heartbeat = tokio::time::interval_at(
                tokio::time::Instant::now() + heartbeat_period,
                heartbeat_period,
            );
            loop {
                tokio::select! {
                    message = rx.recv() => match message {
                        Some(message) => {
                            if write.send(message).await.is_err() {
                                break;
                            }
                        }
                        None => break,
                    },
                    _ = heartbeat.tick() => {
                        if write.send(Message::Ping(Vec::new().into())).await.is_err() {
                            break;
                        }
                    }
                }
            }
        });

        while let Some(message) = read.next().await {
            match message {
                Ok(Message::Text(raw)) => {
                    let handler_fn = self.handler_fn.clone();
                    let client = client.clone();
                    let tx = tx.clone();
                    tokio::spawn(async move {
                        handle_request(handler_fn, client, tx, raw.to_string()).await;
                    });
                }
                Ok(Message::Close(frame)) => {
                    log::info!("WebSocket closed or errored: {frame:?}");
                    break;
                }
                Err(e) => {
                    log::info!("WebSocket closed or errored: {e}");
                    break;
                }
                _ => {}
            }
        }
    }

    pub fn stop(&self) {
        self.running.store(false, Ordering::SeqCst);
    }
}

async fn handle_request(
    handler_fn: Option<HandlerFn>,
    client: reqwest::Client,
    tx: UnboundedSender<Message>,
    raw: String,
) {
    let request: RelayRequest = match serde_json::from_str(&raw) {
        Ok(request) => request,
        Err(e) => {
            log::error!("failed to parse relay request: {e}");
            return;
        }
    };

    if let Some(handler_fn) = handler_fn {
        log::info!("[handler] running handler (request {})", request.id);
        let relay_response = execute_handler(&handler_fn, &request).await;
        let status_code = relay_response.status_code;
        send_message(&tx, &relay_response);
        log::info!("[handler] {} (request {})", status_code, request.id);
        return;
    }

    if let Err(e) = forward_request(&client, &tx, &request).await {
        log::error!("request {} failed: {e}", request.id);
        let error_response = RelayResponse {
            id: request.id.clone(),
            r#type: RelayResponseType::Error,
            status_code: 502,
            headers: HashMap::new(),
            body: serde_json::json!({ "error": e.to_string() }).to_string(),
        };
        send_message(&tx, &error_response);
    }
}

async fn forward_request(
    client: &reqwest::Client,
    tx: &UnboundedSender<Message>,
    request: &RelayRequest,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let method = reqwest::Method::from_bytes(request.method.as_bytes())?;
    let mut builder = client
        .request(method, &request.url)
        .timeout(Duration::from_secs(request.timeout));
    for (name, value) in &request.headers {
        builder = builder.header(name.as_str(), value.as_str());
    }
    if request.method.to_uppercase() != "GET" {
        if let Some(body) = &request.body {
            builder = builder.json(body);
        }
    }

    log::info!(
        "[relay] {} {} (request {})",
        request.method,
        request.url,
        request.id
    );
    let response = builder.send().await?;
    let is_streaming = matches!(
        request.response_mode,
        ResponseMode::SseStreaming | ResponseMode::HttpStreaming
    );
    let status_code = response.status().as_u16();
    let headers = header_map(response.headers());

    if is_streaming {
        // Send response headers first
        let header_msg = RelayResponse {
            id: request.id.clone(),
            r#type: RelayResponseType::Chunk,
            status_code,
            headers,
            body: String::new(),
        };
        send_message(tx, &header_msg);

        // Stream chunks as they arrive
        let mut stream = response.bytes_stream();
        while let Some(raw_chunk) = stream.next().await {
            let raw_chunk = raw_chunk?;
            let text = String::from_utf8_lossy(&raw_chunk).to_string();
            if text.is_empty() {
                continue;
            }
            let chunk_msg = RelayResponse {
                id: request.id.clone(),
                r#type: RelayResponseType::Chunk,
                status_code,
                headers: HashMap::new(),
                body: text,
            };
            send_message(tx, &chunk_msg);
        }

        // Signal stream end
        let end_msg = RelayResponse {
            id: request.id.clone(),
            r#type: RelayResponseType::StreamEnd,
            status_code,
            headers: HashMap::new(),
            body: String::new(),
        };
        send_message(tx, &end_msg);
        log::info!(
            "[relay] {} {} : {} streamed (request {})",
            request.method,
            request.url,
            status_code,
            request.id
        );
    } else {
        let response_body = response.text().await?;
        log::info!(
            "[relay] {} {} : {} (request {})",
            request.method,
            request.url,
            status_code,
            request.id
        );

        let relay_response = RelayResponse {
            id: request.id.clone(),
            r#type: RelayResponseType::Response,
            status_code,
            headers,
            body: response_body,
        };
        send_message(tx, &relay_response);
    }
    Ok(())
}

fn header_map(headers: &reqwest::header::HeaderMap) -> HashMap<String, String> {
    let mut map = HashMap::new();
    for (name, value) in headers {
        map.entry(name.to_string())
            .or_insert_with(|| String::from_utf8_lossy(value.as_bytes()).to_string());
    }
    map
}

fn send_message(tx: &UnboundedSender<Message>, response: &RelayResponse) {
    match serde_json::to_string(response) {
        Ok(text) => {
            if tx.send(Message::Text(text.into())).is_err() {
                log::error!(
                    "failed to send response for request {}: connection closed",
                    response.id
                );
            }
        }
        Err(e) => {
            log::error!("failed to serialize response for request {}: {e}", response.id);
        }
    }
}

pub fn create_agent() -> Result<RelayAgent, String> {
    let api_key = confident_api_key();
    if api_key.is_empty() {
        return Err("CONFIDENT_API_KEY is required".to_string());
    }
    let server_url = confident_ws_base_url();
    if server_url.is_empty() {
        return Err("CONFIDENT_WS_BASE_URL is required".to_string());
    }

    let registered = registered_handlers();
    if registered.len() > 1 {
        return Err(format!(
            "multiple handler functions registered ({}) — exactly one is allowed per agent",
            registered.len()
        ));
    }
    let handler_fn = registered.into_iter().next();
    if handler_fn.is_some() {
        log::info!("handler mode: using registered handler");
    }

    Ok(RelayAgent::new(server_url, api_key, handler_fn))
}
