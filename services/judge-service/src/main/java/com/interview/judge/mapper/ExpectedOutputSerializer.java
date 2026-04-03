package com.interview.judge.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExpectedOutputSerializer {

    private final ObjectMapper objectMapper;

    /**
     * Serializes the {@code expectedOutput} map (e.g. {@code {"value": 2}}) to a JSON string
     * for storage in {@code judge_task_results.expected_output}.
     *
     * <p>Referenced by name in {@link JudgeMapper} via {@code qualifiedByName}.
     */
    @Named("serializeExpectedOutput")
    public String serialize(Map<String, Object> expectedOutput) {
        try {
            return objectMapper.writeValueAsString(expectedOutput);
        } catch (Exception e) {
            log.error("Failed to serialize expectedOutput: {}", e.getMessage());
            return "{}";
        }
    }
}
