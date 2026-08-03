package ai.confident.agent.handler;

import ai.confident.agent.schemas.Request;

@FunctionalInterface
public interface HandlerFunction {
    Object apply(Request request) throws Exception;
}
