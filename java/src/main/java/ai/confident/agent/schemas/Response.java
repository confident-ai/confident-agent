package ai.confident.agent.schemas;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"output", "retrieval_context", "tools_called", "state"})
public class Response {

    public String output;

    @JsonProperty("retrieval_context")
    public List<String> retrievalContext;

    @JsonProperty("tools_called")
    public List<ToolCall> toolsCalled;

    public Object state;

    public Response() {
    }

    public Response(String output) {
        this.output = output;
    }
}
