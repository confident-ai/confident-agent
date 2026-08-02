package confidentagent

import "os"

const (
	HeartbeatIntervalS     = 45
	InitialReconnectDelayS = 1
	MaxReconnectDelayS     = 45
)

func confidentAPIKey() string {
	return os.Getenv("CONFIDENT_API_KEY")
}

func confidentWSBaseURL() string {
	if url := os.Getenv("CONFIDENT_WS_BASE_URL"); url != "" {
		return url
	}
	return "wss://deepeval.confident-ai.com/ws/relay"
}
