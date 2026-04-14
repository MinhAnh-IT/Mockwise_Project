package com.mockwise.questionbank.entity;

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
}
