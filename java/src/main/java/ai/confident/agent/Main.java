package ai.confident.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        System.setProperty(
                "java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT,%1$tL [%3$s] %4$s: %5$s%n");
        loadDotenv();

        Logger logger = Logger.getLogger("relay-agent");
        RelayAgent agent = RelayAgent.createAgent();
        Runtime.getRuntime().addShutdownHook(new Thread(agent::stop));

        logger.info("starting relay agent");
        agent.start();
        logger.info("relay agent stopped");
    }

    private static void loadDotenv() {
        Path file = Path.of(".env");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                if (key.startsWith("export ")) {
                    key = key.substring("export ".length()).trim();
                }
                String value = trimmed.substring(eq + 1).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            Logger.getLogger("relay-agent").warning("failed to load .env: " + e);
        }
    }
}
