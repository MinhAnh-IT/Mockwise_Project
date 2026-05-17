package com.mockwise.questionbank.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TestCase {

    String id;
    Map<String, Object> inputData;
    Map<String, Object> expectedOutput;

    @JsonProperty("is_hidden")
    boolean hidden;

    /**
     * Optional human explanation for this case (LeetCode "Explanation").
     * Only meaningful on visible cases; serialized into the test_cases jsonb
     * and surfaced to the candidate workspace via CodingProblemView. Omitted
     * from JSON when null so existing snapshots stay clean.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String note;
}
