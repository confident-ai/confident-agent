package ai.confident.agent.schemas;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RelayResponseType {
    RESPONSE("response"),
    ERROR("error"),
    CHUNK("chunk"),
    STREAM_END("stream_end");

    private final String value;

    RelayResponseType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
