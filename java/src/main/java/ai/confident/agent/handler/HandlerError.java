package ai.confident.agent.handler;

public class HandlerError extends RuntimeException {

    public HandlerError(String message) {
        super(message);
    }

    public HandlerError(String message, Throwable cause) {
        super(message, cause);
    }
}
