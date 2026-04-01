package com.interview.judge.codebuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.judge.dto.FunctionMeta;
import com.interview.judge.dto.ParamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class StdinBuilder {

    private final ObjectMapper objectMapper;
    private final TypeSerializer typeSerializer;

    /**
     * Builds the stdin string for Judge0 according to the UniversalDriver protocol:
     *
     * <pre>
     * Line 1: functionMeta JSON (single line)
     * Line 2: serialized value of param[0]
     * Line 3: serialized value of param[1]
     * ...
     * </pre>
     *
     * @param functionMeta function metadata
     * @param inputData    map of param name → value (from Kafka test case)
     * @return complete stdin string
     */
    public String build(FunctionMeta functionMeta, Map<String, Object> inputData) throws JsonProcessingException {
        StringBuilder sb = new StringBuilder();

        // Line 1: functionMeta as JSON
        String metaJson = objectMapper.writeValueAsString(functionMeta);
        sb.append(metaJson).append("\n");

        // Lines 2+: each param serialized in order
        for (ParamMeta param : functionMeta.getParams()) {
            Object value = inputData.get(param.getName());
            String serialized = typeSerializer.serialize(value, param.getType());
            log.debug("Stdin param '{}' (type={}): {}", param.getName(), param.getType(), serialized);
            sb.append(serialized).append("\n");
        }

        return sb.toString();
    }
}
