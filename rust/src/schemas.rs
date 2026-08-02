use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ResponseMode {
    #[default]
    HttpResponse,
    SseStreaming,
    HttpStreaming,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RelayRequest {
    pub id: String,
    pub method: String,
    pub url: String,
    #[serde(default)]
    pub headers: HashMap<String, String>,
    #[serde(default)]
    pub body: Option<Value>,
    #[serde(default = "default_timeout")]
    pub timeout: u64,
    #[serde(default)]
    pub response_mode: ResponseMode,
}

fn default_timeout() -> u64 {
    60
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum RelayResponseType {
    Response,
    Error,
    Chunk,
    StreamEnd,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RelayResponse {
    pub id: String,
    pub r#type: RelayResponseType,
    #[serde(rename = "statusCode")]
    pub status_code: u16,
    #[serde(default)]
    pub headers: HashMap<String, String>,
    pub body: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToolCall {
    pub name: String,
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub input_parameters: Option<Value>,
    #[serde(default)]
    pub output: Option<Value>,
    #[serde(flatten)]
    pub extra: HashMap<String, Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Turn {
    pub role: String,
    pub content: String,
    #[serde(flatten)]
    pub extra: HashMap<String, Value>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Request {
    #[serde(default)]
    pub input: String,
    #[serde(default)]
    pub context: Vec<String>,
    #[serde(default)]
    pub retrieval_context: Vec<String>,
    #[serde(default)]
    pub expected_output: Option<String>,
    #[serde(default)]
    pub turns: Vec<Turn>,
    #[serde(default)]
    pub scenario: Option<String>,
    #[serde(default)]
    pub state: Option<Value>,
    #[serde(default)]
    pub prompts: Option<Value>,
    #[serde(default)]
    pub hyperparameters: Option<Value>,
    #[serde(default)]
    pub test_case_id: Option<String>,
    #[serde(default)]
    pub turn_id: Option<String>,
    #[serde(flatten)]
    pub extra: HashMap<String, Value>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Response {
    pub output: String,
    #[serde(default)]
    pub retrieval_context: Option<Vec<String>>,
    #[serde(default)]
    pub tools_called: Option<Vec<ToolCall>>,
    #[serde(default)]
    pub state: Option<Value>,
}

impl From<String> for Response {
    fn from(output: String) -> Self {
        Response {
            output,
            ..Response::default()
        }
    }
}

impl From<&str> for Response {
    fn from(output: &str) -> Self {
        Response::from(output.to_string())
    }
}
