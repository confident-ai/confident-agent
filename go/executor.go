package confidentagent

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"
)

type HandlerError struct {
	Message string
}

func (e *HandlerError) Error() string {
	return e.Message
}

func executeHandler(fn HandlerFunc, request RelayRequest) RelayResponse {
	var handlerRequest Request
	if body, ok := request.Body.(map[string]any); ok {
		raw, err := json.Marshal(body)
		if err != nil {
			logError("handler raised for request %s: %v", request.ID, err)
			return errorResponse(request.ID, err.Error())
		}
		if err := json.Unmarshal(raw, &handlerRequest); err != nil {
			logError("handler raised for request %s: %v", request.ID, err)
			return errorResponse(request.ID, err.Error())
		}
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(request.Timeout)*time.Second)
	defer cancel()

	type handlerResult struct {
		value any
		err   error
	}
	results := make(chan handlerResult, 1)
	go func() {
		defer func() {
			if recovered := recover(); recovered != nil {
				results <- handlerResult{err: fmt.Errorf("handler panicked: %v", recovered)}
			}
		}()
		value, err := fn(handlerRequest)
		results <- handlerResult{value: value, err: err}
	}()

	select {
	case <-ctx.Done():
		logError("handler timed out after %ds (request %s)", request.Timeout, request.ID)
		return errorResponse(request.ID, fmt.Sprintf("handler timed out after %ds", request.Timeout))
	case result := <-results:
		if result.err != nil {
			logError("handler raised for request %s: %v", request.ID, result.err)
			return errorResponse(request.ID, result.err.Error())
		}
		response, err := normalize(result.value)
		if err != nil {
			logError("handler raised for request %s: %v", request.ID, err)
			return errorResponse(request.ID, err.Error())
		}
		body, err := json.Marshal(response)
		if err != nil {
			logError("handler raised for request %s: %v", request.ID, err)
			return errorResponse(request.ID, err.Error())
		}
		return RelayResponse{
			ID:         request.ID,
			Type:       RelayResponseTypeResponse,
			StatusCode: 200,
			Headers:    map[string]string{"Content-Type": "application/json"},
			Body:       string(body),
		}
	}
}

func normalize(result any) (Response, error) {
	switch value := result.(type) {
	case Response:
		return value, nil
	case *Response:
		if value == nil {
			return Response{}, errors.New("handler returned nil — return a string or a Response")
		}
		return *value, nil
	case string:
		return Response{Output: value}, nil
	case nil:
		return Response{}, errors.New("handler returned nil — return a string or a Response")
	default:
		return Response{}, fmt.Errorf("handler returned %T — return a string or a Response", result)
	}
}

func errorResponse(requestID string, message string) RelayResponse {
	return RelayResponse{
		ID:         requestID,
		Type:       RelayResponseTypeError,
		StatusCode: 500,
		Headers:    map[string]string{"Content-Type": "application/json"},
		Body:       marshalError(message),
	}
}

func marshalError(message string) string {
	body, _ := json.Marshal(map[string]string{"error": message})
	return string(body)
}
