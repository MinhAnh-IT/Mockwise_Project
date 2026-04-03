package com.interview.judge.codebuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TypeSerializer {

    ObjectMapper objectMapper;

    public TypeSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes a Java value (from Kafka JSON deserialization) to the string
     * format expected by the UniversalDriver on stdin.
     *
     * <p>Rules:
     * <ul>
     *   <li>int/long/double/boolean → {@code String.valueOf(value)}</li>
     *   <li>string → raw string, no wrapping quotes</li>
     *   <li>int[], long[], double[], string[], int[][], char[][] → JSON array string</li>
     *   <li>TreeNode → level-order JSON array (e.g. {@code [3,9,20,null,null,15,7]})</li>
     *   <li>ListNode → JSON int array (e.g. {@code [1,2,3,4,5]})</li>
     * </ul>
     *
     * @param value deserialized JSON value (Integer, Long, Double, Boolean, String, List, Map, …)
     * @param type  param type string from FunctionMeta
     * @return string representation for stdin
     */
    public String serialize(Object value, String type) throws JsonProcessingException {
        if (value == null) {
            return "null";
        }

        return switch (type) {
            case "int", "long", "double", "boolean" -> String.valueOf(value);
            case "string", "String"                 -> String.valueOf(value);
            // char: sent as raw character — driver reads charAt(0)
            case "char", "Character"                -> String.valueOf(value);
            // Arrays, matrices, and tree/list structures: Jackson already holds them as List/Map
            // which re-serializes to the same JSON format the driver expects.
            case "int[]", "long[]", "double[]", "string[]", "String[]",
                 "int[][]", "char[][]", "String[][]",
                 "List<Integer>", "List<String>",
                 "List<List<Integer>>", "List<List<String>>",
                 "TreeNode", "ListNode" ->
                    objectMapper.writeValueAsString(value);
            default -> objectMapper.writeValueAsString(value);
        };
    }
}
