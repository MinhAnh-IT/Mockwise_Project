package com.interview.judge.codebuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutputComparator {

    private final ObjectMapper objectMapper;

    /**
     * Compares the program's stdout against the expected output.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Parse {@code stdout} → JsonNode {@code actual}</li>
     *   <li>Parse {@code expectedOutputJson} → JsonNode, extract {@code "value"} field</li>
     *   <li>If {@code orderMatters=false} and both nodes are arrays → sort then compare</li>
     *   <li>Otherwise → {@code JsonNode.equals()}</li>
     *   <li>Fallback on parse error → trimmed string comparison</li>
     * </ol>
     *
     * @param stdout            raw stdout string from Judge0 (already base64-decoded)
     * @param expectedOutputJson JSON string of the form {@code {"value": ...}}
     * @param orderMatters      whether array element order matters
     * @return {@code true} if the output is correct
     */
    public boolean compare(String stdout, String expectedOutputJson, boolean orderMatters) {
        if (stdout == null || stdout.isBlank()) {
            log.debug("stdout is null/blank — WA");
            return false;
        }

        try {
            JsonNode actual  = objectMapper.readTree(stdout.trim());
            JsonNode wrapper = objectMapper.readTree(expectedOutputJson);
            JsonNode expected = wrapper.get("value");

            if (expected == null) {
                log.warn("expectedOutput JSON has no 'value' field: {}", expectedOutputJson);
                return false;
            }

            if (!orderMatters && actual.isArray() && expected.isArray()) {
                List<JsonNode> sortedActual   = sortedNodes(actual);
                List<JsonNode> sortedExpected = sortedNodes(expected);
                return sortedActual.equals(sortedExpected);
            }

            return actual.equals(expected);

        } catch (Exception e) {
            log.warn("JSON comparison failed, falling back to string compare. stdout='{}', expected='{}'",
                    stdout, expectedOutputJson, e);
            return fallbackStringCompare(stdout, expectedOutputJson);
        }
    }

    private List<JsonNode> sortedNodes(JsonNode arrayNode) {
        List<JsonNode> list = new ArrayList<>();
        arrayNode.forEach(list::add);
        list.sort(Comparator.comparing(JsonNode::toString));
        return list;
    }

    private boolean fallbackStringCompare(String stdout, String expectedOutputJson) {
        try {
            JsonNode wrapper  = objectMapper.readTree(expectedOutputJson);
            JsonNode expected = wrapper.get("value");
            if (expected == null) return false;

            String expectedStr = expected.isTextual() ? expected.asText() : expected.toString();
            return stdout.trim().equals(expectedStr.trim());
        } catch (Exception ex) {
            log.error("Fallback string compare also failed", ex);
            return false;
        }
    }
}
