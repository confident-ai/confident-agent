package ai.confident.agent.handler;

import java.util.ArrayList;
import java.util.List;

public final class Decorator {

    private static final List<HandlerFunction> registered = new ArrayList<>();

    private Decorator() {
    }

    public static void handler(HandlerFunction fn) {
        synchronized (registered) {
            registered.add(fn);
        }
    }

    public static HandlerFunction registeredHandler() {
        synchronized (registered) {
            if (registered.isEmpty()) {
                return null;
            }
            if (registered.size() > 1) {
                throw new HandlerError(
                        "multiple handler functions registered ("
                                + registered.size()
                                + ") — exactly one is allowed per agent");
            }
            return registered.get(0);
        }
    }
}
