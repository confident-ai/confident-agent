use std::io::Write;

#[tokio::main]
async fn main() {
    dotenvy::dotenv().ok();

    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info"))
        .format(|buf, record| {
            writeln!(
                buf,
                "{} [relay-agent] {}: {}",
                buf.timestamp_millis(),
                record.level(),
                record.args()
            )
        })
        .init();

    if let Err(e) = confident_agent::run().await {
        log::error!("{e}");
        std::process::exit(1);
    }
}
