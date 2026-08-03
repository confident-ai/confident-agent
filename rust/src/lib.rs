pub mod agent;
pub mod config;
pub mod handler;
pub mod schemas;

pub use agent::{create_agent, RelayAgent};
pub use handler::{handler, HandlerError};
pub use schemas::{Request, Response, ToolCall, Turn};

use std::sync::Arc;

pub async fn run() -> Result<(), String> {
    let agent = Arc::new(create_agent()?);

    let signal_agent = agent.clone();
    tokio::spawn(async move {
        wait_for_shutdown().await;
        signal_agent.stop();
    });

    log::info!("starting relay agent");
    agent.start().await;
    log::info!("relay agent stopped");
    Ok(())
}

async fn wait_for_shutdown() {
    #[cfg(unix)]
    {
        use tokio::signal::unix::{signal, SignalKind};
        match (
            signal(SignalKind::interrupt()),
            signal(SignalKind::terminate()),
        ) {
            (Ok(mut sigint), Ok(mut sigterm)) => {
                tokio::select! {
                    _ = sigint.recv() => {}
                    _ = sigterm.recv() => {}
                }
            }
            _ => {
                let _ = tokio::signal::ctrl_c().await;
            }
        }
    }
    #[cfg(not(unix))]
    {
        let _ = tokio::signal::ctrl_c().await;
    }
}
