package com.mockwise.questionbank.dto.response;

import com.mockwise.questionbank.entity.FunctionMeta;
import com.mockwise.questionbank.entity.StarterCode;
import com.mockwise.questionbank.entity.TestCase;
import com.mockwise.questionbank.enums.Difficulty;
import com.mockwise.questionbank.enums.QuestionStatus;
import com.mockwise.questionbank.enums.QuestionType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CodingQuestionResponse {

    String id;
    QuestionType type;
    Difficulty difficulty;
    QuestionStatus status;
    List<String> tags;
    String title;
    String description;
    int timeLimitMinutes;
    String optimalTimeComplexity;
    String optimalSpaceComplexity;
    FunctionMeta functionMeta;
    StarterCode starterCode;
    List<TestCase> testCases;
    String createdBy;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;
}
