use std::collections::HashMap;
use std::fmt;
use std::time::Duration;

use serde_json::{json, Value};

use crate::schemas::{RelayRequest, RelayResponse, RelayResponseType, Request};

use super::decorator::HandlerFn;

#[derive(Debug)]
pub struct HandlerError(String);

impl HandlerError {
    pub fn new(message: impl Into<String>) -> Self {
        HandlerError(message.into())
    }
}

impl fmt::Display for HandlerError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl std::error::Error for HandlerError {}

impl From<String> for HandlerError {
    fn from(message: String) -> Self {
        HandlerError(message)
    }
}

impl From<&str> for HandlerError {
    fn from(message: &str) -> Self {
        HandlerError(message.to_string())
    }
}

pub async fn execute_handler(f: &HandlerFn, request: &RelayRequest) -> RelayResponse {
    let handler_request = match &request.body {
        Some(body @ Value::Object(_)) => match serde_json::from_value::<Request>(body.clone()) {
            Ok(handler_request) => handler_request,
            Err(e) => {
                log::error!("handler raised for request {}: {e}", request.id);
                return error_response(&request.id, &e.to_string());
            }
        },
        _ => Request::default(),
    };

    match tokio::time::timeout(Duration::from_secs(request.timeout), f(handler_request)).await {
        Ok(Ok(response)) => match serde_json::to_string(&response) {
            Ok(body) => RelayResponse {
                id: request.id.clone(),
                r#type: RelayResponseType::Response,
                status_code: 200,
                headers: HashMap::from([(
                    "Content-Type".to_string(),
                    "application/json".to_string(),
                )]),
                body,
            },
            Err(e) => {
                log::error!("handler raised for request {}: {e}", request.id);
                error_response(&request.id, &e.to_string())
            }
        },
        Ok(Err(e)) => {
            log::error!("handler raised for request {}: {e}", request.id);
            error_response(&request.id, &e.to_string())
        }
        Err(_) => {
            log::error!(
                "handler timed out after {}s (request {})",
                request.timeout,
                request.id
            );
            error_response(
                &request.id,
                &format!("handler timed out after {}s", request.timeout),
            )
        }
    }
}

fn error_response(request_id: &str, message: &str) -> RelayResponse {
    RelayResponse {
        id: request_id.to_string(),
        r#type: RelayResponseType::Error,
        status_code: 500,
        headers: HashMap::from([("Content-Type".to_string(), "application/json".to_string())]),
        body: json!({ "error": message }).to_string(),
    }
}
