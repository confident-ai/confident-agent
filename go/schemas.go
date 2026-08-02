package confidentagent

import "encoding/json"

type ResponseMode string

const (
	ResponseModeHTTPResponse  ResponseMode = "HTTP_RESPONSE"
	ResponseModeSSEStreaming  ResponseMode = "SSE_STREAMING"
	ResponseModeHTTPStreaming ResponseMode = "HTTP_STREAMING"
)

type RelayRequest struct {
	ID           string            `json:"id"`
	Method       string            `json:"method"`
	URL          string            `json:"url"`
	Headers      map[string]string `json:"headers"`
	Body         any               `json:"body"`
	Timeout      int               `json:"timeout"`
	ResponseMode ResponseMode      `json:"response_mode"`
}

func (r *RelayRequest) UnmarshalJSON(data []byte) error {
	type relayRequest RelayRequest
	var raw relayRequest
	if err := json.Unmarshal(data, &raw); err != nil {
		return err
	}
	if raw.Headers == nil {
		raw.Headers = map[string]string{}
	}
	if raw.Timeout == 0 {
		raw.Timeout = 60
	}
	if raw.ResponseMode == "" {
		raw.ResponseMode = ResponseModeHTTPResponse
	}
	*r = RelayRequest(raw)
	return nil
}

type RelayResponseType string

const (
	RelayResponseTypeResponse  RelayResponseType = "response"
	RelayResponseTypeError     RelayResponseType = "error"
	RelayResponseTypeChunk     RelayResponseType = "chunk"
	RelayResponseTypeStreamEnd RelayResponseType = "stream_end"
)

type RelayResponse struct {
	ID         string            `json:"id"`
	Type       RelayResponseType `json:"type"`
	StatusCode int               `json:"statusCode"`
	Headers    map[string]string `json:"headers"`
	Body       string            `json:"body"`
}

func (r RelayResponse) MarshalJSON() ([]byte, error) {
	type relayResponse RelayResponse
	if r.Headers == nil {
		r.Headers = map[string]string{}
	}
	return json.Marshal(relayResponse(r))
}

type ToolCall struct {
	Name            string         `json:"name"`
	Description     *string        `json:"description"`
	InputParameters map[string]any `json:"input_parameters"`
	Output          any            `json:"output"`
}

type Turn struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type Request struct {
	Input            string         `json:"input"`
	Context          []string       `json:"context"`
	RetrievalContext []string       `json:"retrieval_context"`
	ExpectedOutput   *string        `json:"expected_output"`
	Turns            []Turn         `json:"turns"`
	Scenario         *string        `json:"scenario"`
	State            any            `json:"state"`
	Prompts          map[string]any `json:"prompts"`
	Hyperparameters  map[string]any `json:"hyperparameters"`
	TestCaseID       *string        `json:"test_case_id"`
	TurnID           *string        `json:"turn_id"`
}

type Response struct {
	Output           string     `json:"output"`
	RetrievalContext []string   `json:"retrieval_context"`
	ToolsCalled      []ToolCall `json:"tools_called"`
	State            any        `json:"state"`
}
