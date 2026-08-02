package ai.confident.agent.schemas;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RelayResponse {

    public String id;
    public RelayResponseType type;
    public int statusCode;
    public Map<String, String> headers = new LinkedHashMap<>();
    public String body;

    public RelayResponse() {
    }

    public RelayResponse(
            String id,
            RelayResponseType type,
            int statusCode,
            Map<String, String> headers,
            String body) {
        this.id = id;
        this.type = type;
        this.statusCode = statusCode;
        this.headers = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
        this.body = body;
    }
}
