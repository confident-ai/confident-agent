use std::future::Future;
use std::pin::Pin;
use std::sync::{Arc, Mutex, OnceLock};

use crate::schemas::{Request, Response};

use super::executor::HandlerError;

pub type HandlerFn = Arc<
    dyn Fn(Request) -> Pin<Box<dyn Future<Output = Result<Response, HandlerError>> + Send>>
        + Send
        + Sync,
>;

fn registry() -> &'static Mutex<Vec<HandlerFn>> {
    static REGISTERED: OnceLock<Mutex<Vec<HandlerFn>>> = OnceLock::new();
    REGISTERED.get_or_init(|| Mutex::new(Vec::new()))
}

pub fn handler<F, Fut, R>(f: F) -> HandlerFn
where
    F: Fn(Request) -> Fut + Send + Sync + 'static,
    Fut: Future<Output = Result<R, HandlerError>> + Send + 'static,
    R: Into<Response>,
{
    let wrapped: HandlerFn = Arc::new(move |request| {
        let fut = f(request);
        Box::pin(async move { fut.await.map(Into::into) })
    });
    if let Ok(mut registered) = registry().lock() {
        registered.push(wrapped.clone());
    }
    wrapped
}

pub(crate) fn registered_handlers() -> Vec<HandlerFn> {
    registry()
        .lock()
        .map(|registered| registered.clone())
        .unwrap_or_default()
}
