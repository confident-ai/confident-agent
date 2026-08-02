package confidentagent

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/gorilla/websocket"
)

func logInfo(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "%s [relay-agent] INFO: %s\n", time.Now().Format("2006-01-02 15:04:05,000"), fmt.Sprintf(format, args...))
}

func logError(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "%s [relay-agent] ERROR: %s\n", time.Now().Format("2006-01-02 15:04:05,000"), fmt.Sprintf(format, args...))
}

type RelayAgent struct {
	ServerURL string
	APIKey    string
	HandlerFn HandlerFunc

	reconnectDelay int
	running        atomic.Bool
	mu             sync.Mutex
	writeMu        sync.Mutex
	conn           *websocket.Conn
}

func NewRelayAgent(serverURL string, apiKey string, handlerFn HandlerFunc) *RelayAgent {
	agent := &RelayAgent{
		ServerURL:      serverURL,
		APIKey:         apiKey,
		HandlerFn:      handlerFn,
		reconnectDelay: InitialReconnectDelayS,
	}
	agent.running.Store(true)
	return agent
}

func (a *RelayAgent) Start(ctx context.Context) error {
	for a.running.Load() {
		if err := a.connect(ctx); err != nil {
			logError("connection error: %v", err)
		}
		if !a.running.Load() || ctx.Err() != nil {
			break
		}
		logInfo("reconnecting in %ds", a.reconnectDelay)
		select {
		case <-ctx.Done():
			return nil
		case <-time.After(time.Duration(a.reconnectDelay) * time.Second):
		}
		a.reconnectDelay = min(a.reconnectDelay*2, MaxReconnectDelayS)
	}
	return nil
}

func (a *RelayAgent) connect(ctx context.Context) error {
	header := http.Header{"Authorization": {"Bearer " + a.APIKey}}
	conn, response, err := websocket.DefaultDialer.DialContext(ctx, a.ServerURL, header)
	if err != nil {
		if response != nil {
			response.Body.Close()
		}
		return err
	}
	defer conn.Close()

	a.mu.Lock()
	a.conn = conn
	a.mu.Unlock()

	logInfo("connected to %s", a.ServerURL)
	a.reconnectDelay = InitialReconnectDelayS

	return a.listen(ctx, conn)
}

func (a *RelayAgent) listen(ctx context.Context, conn *websocket.Conn) error {
	readWait := 2 * HeartbeatIntervalS * time.Second
	conn.SetReadDeadline(time.Now().Add(readWait))
	conn.SetPongHandler(func(string) error {
		return conn.SetReadDeadline(time.Now().Add(readWait))
	})

	done := make(chan struct{})
	defer close(done)

	go func() {
		ticker := time.NewTicker(HeartbeatIntervalS * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-done:
				return
			case <-ctx.Done():
				conn.Close()
				return
			case <-ticker.C:
				if err := conn.WriteControl(websocket.PingMessage, nil, time.Now().Add(10*time.Second)); err != nil {
					return
				}
			}
		}
	}()

	for {
		messageType, raw, err := conn.ReadMessage()
		if err != nil {
			return err
		}
		if messageType == websocket.TextMessage {
			go a.handleRequest(ctx, conn, raw)
		}
	}
}

func (a *RelayAgent) handleRequest(ctx context.Context, conn *websocket.Conn, raw []byte) {
	var request RelayRequest
	if err := json.Unmarshal(raw, &request); err != nil {
		logError("failed to parse relay request: %v", err)
		return
	}

	if a.HandlerFn != nil {
		logInfo("[handler] running handler (request %s)", request.ID)
		relayResponse := executeHandler(a.HandlerFn, request)
		a.sendJSON(conn, relayResponse)
		logInfo("[handler] %d (request %s)", relayResponse.StatusCode, request.ID)
		return
	}

	var bodyReader io.Reader
	hasBody := strings.ToUpper(request.Method) != "GET" && request.Body != nil
	if hasBody {
		payload, err := json.Marshal(request.Body)
		if err != nil {
			a.sendRequestError(conn, request.ID, err)
			return
		}
		bodyReader = bytes.NewReader(payload)
	}

	requestCtx, cancel := context.WithTimeout(ctx, time.Duration(request.Timeout)*time.Second)
	defer cancel()

	httpRequest, err := http.NewRequestWithContext(requestCtx, request.Method, request.URL, bodyReader)
	if err != nil {
		a.sendRequestError(conn, request.ID, err)
		return
	}
	if hasBody {
		httpRequest.Header.Set("Content-Type", "application/json")
	}
	for key, value := range request.Headers {
		httpRequest.Header.Set(key, value)
	}

	logInfo("[relay] %s %s (request %s)", request.Method, request.URL, request.ID)
	response, err := http.DefaultClient.Do(httpRequest)
	if err != nil {
		a.sendRequestError(conn, request.ID, err)
		return
	}
	defer response.Body.Close()

	isStreaming := request.ResponseMode == ResponseModeSSEStreaming ||
		request.ResponseMode == ResponseModeHTTPStreaming

	if isStreaming {
		// Send response headers first
		a.sendJSON(conn, RelayResponse{
			ID:         request.ID,
			Type:       RelayResponseTypeChunk,
			StatusCode: response.StatusCode,
			Headers:    flattenHeaders(response.Header),
			Body:       "",
		})

		// Stream chunks as they arrive
		reader := bufio.NewReader(response.Body)
		buffer := make([]byte, 4096)
		for {
			n, readErr := reader.Read(buffer)
			if n > 0 {
				a.sendJSON(conn, RelayResponse{
					ID:         request.ID,
					Type:       RelayResponseTypeChunk,
					StatusCode: response.StatusCode,
					Headers:    map[string]string{},
					Body:       string(buffer[:n]),
				})
			}
			if readErr != nil {
				if !errors.Is(readErr, io.EOF) {
					a.sendRequestError(conn, request.ID, readErr)
					return
				}
				break
			}
		}

		// Signal stream end
		a.sendJSON(conn, RelayResponse{
			ID:         request.ID,
			Type:       RelayResponseTypeStreamEnd,
			StatusCode: response.StatusCode,
			Headers:    map[string]string{},
			Body:       "",
		})
		logInfo("[relay] %s %s : %d streamed (request %s)", request.Method, request.URL, response.StatusCode, request.ID)
	} else {
		responseBody, readErr := io.ReadAll(response.Body)
		if readErr != nil {
			a.sendRequestError(conn, request.ID, readErr)
			return
		}
		logInfo("[relay] %s %s : %d (request %s)", request.Method, request.URL, response.StatusCode, request.ID)

		a.sendJSON(conn, RelayResponse{
			ID:         request.ID,
			Type:       RelayResponseTypeResponse,
			StatusCode: response.StatusCode,
			Headers:    flattenHeaders(response.Header),
			Body:       string(responseBody),
		})
	}
}

func (a *RelayAgent) sendJSON(conn *websocket.Conn, response RelayResponse) {
	payload, err := json.Marshal(response)
	if err != nil {
		logError("failed to serialize relay response for request %s: %v", response.ID, err)
		return
	}
	a.writeMu.Lock()
	defer a.writeMu.Unlock()
	if err := conn.WriteMessage(websocket.TextMessage, payload); err != nil {
		logError("failed to send relay response for request %s: %v", response.ID, err)
	}
}

func (a *RelayAgent) sendRequestError(conn *websocket.Conn, requestID string, err error) {
	logError("request %s failed: %v", requestID, err)
	a.sendJSON(conn, RelayResponse{
		ID:         requestID,
		Type:       RelayResponseTypeError,
		StatusCode: 502,
		Headers:    map[string]string{},
		Body:       marshalError(err.Error()),
	})
}

func flattenHeaders(header http.Header) map[string]string {
	headers := map[string]string{}
	for key, values := range header {
		if len(values) > 0 {
			headers[key] = values[0]
		}
	}
	return headers
}

func (a *RelayAgent) Stop() {
	a.running.Store(false)
	a.mu.Lock()
	if a.conn != nil {
		a.conn.Close()
	}
	a.mu.Unlock()
}

func CreateAgent() (*RelayAgent, error) {
	if confidentAPIKey() == "" {
		return nil, errors.New("CONFIDENT_API_KEY is required")
	}
	if confidentWSBaseURL() == "" {
		return nil, errors.New("CONFIDENT_WS_BASE_URL is required")
	}

	var handlerFn HandlerFunc
	if len(registered) > 1 {
		return nil, &HandlerError{Message: fmt.Sprintf(
			"multiple handler functions registered (%d) — exactly one is allowed per agent",
			len(registered),
		)}
	}
	if len(registered) == 1 {
		handlerFn = registered[0]
		logInfo("handler mode: using registered handler")
	}

	return NewRelayAgent(confidentWSBaseURL(), confidentAPIKey(), handlerFn), nil
}

func Run() error {
	agent, err := CreateAgent()
	if err != nil {
		return err
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()
	go func() {
		<-ctx.Done()
		agent.Stop()
	}()

	logInfo("starting relay agent")
	err = agent.Start(ctx)
	logInfo("relay agent stopped")
	return err
}
