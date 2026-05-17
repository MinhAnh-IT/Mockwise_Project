package com.mockwise.questionbank.dto.request;

import com.mockwise.questionbank.entity.FunctionMeta;
import com.mockwise.questionbank.entity.StarterCode;
import com.mockwise.questionbank.entity.TestCase;
import com.mockwise.questionbank.enums.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CodingQuestionRequest {

    @NotNull(message = "difficulty is required")
    Difficulty difficulty;

    List<String> tags;

    @NotBlank(message = "title is required")
    String title;

    @NotBlank(message = "description is required")
    String description;

    /** Optional LeetCode-style constraints block (markdown). */
    String constraints;

    @NotBlank(message = "optimalTimeComplexity is required")
    String optimalTimeComplexity;

    @NotBlank(message = "optimalSpaceComplexity is required")
    String optimalSpaceComplexity;

    @NotNull(message = "functionMeta is required")
    @Valid
    FunctionMeta functionMeta;

    StarterCode starterCode;

    @NotEmpty(message = "testCases must not be empty")
    @Valid
    List<TestCase> testCases;
}
