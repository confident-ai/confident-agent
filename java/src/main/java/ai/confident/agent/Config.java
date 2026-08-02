package ai.confident.agent;

public final class Config {

    public static final String CONFIDENT_API_KEY = env("CONFIDENT_API_KEY", "");
    public static final String CONFIDENT_WS_BASE_URL =
            env("CONFIDENT_WS_BASE_URL", "wss://deepeval.confident-ai.com/ws/relay");

    public static final int HEARTBEAT_INTERVAL_S = 45;
    public static final int INITIAL_RECONNECT_DELAY_S = 1;
    public static final int MAX_RECONNECT_DELAY_S = 45;

    private Config() {
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            value = System.getProperty(key);
        }
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return value;
    }
}
