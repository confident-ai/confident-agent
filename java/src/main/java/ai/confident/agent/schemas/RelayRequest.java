package ai.confident.agent.schemas;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RelayRequest {

    public String id;
    public String method;
    public String url;
    public Map<String, String> headers = new LinkedHashMap<>();
    public Object body;
    public int timeout = 60;

    @JsonProperty("response_mode")
    public ResponseMode responseMode = ResponseMode.HTTP_RESPONSE;
}
