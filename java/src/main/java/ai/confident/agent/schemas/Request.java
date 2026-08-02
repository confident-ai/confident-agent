package ai.confident.agent.schemas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Request {

    public String input = "";
    public List<String> context = new ArrayList<>();

    @JsonProperty("retrieval_context")
    public List<String> retrievalContext = new ArrayList<>();

    @JsonProperty("expected_output")
    public String expectedOutput;

    public List<Turn> turns = new ArrayList<>();
    public String scenario;
    public Object state;
    public Map<String, Object> prompts;
    public Map<String, Object> hyperparameters;

    @JsonProperty("test_case_id")
    public String testCaseId;

    @JsonProperty("turn_id")
    public String turnId;

    private final Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnySetter
    public void setExtra(String key, Object value) {
        extra.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtra() {
        return extra;
    }
}
