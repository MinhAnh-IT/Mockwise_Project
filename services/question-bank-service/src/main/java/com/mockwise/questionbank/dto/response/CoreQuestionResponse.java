package com.mockwise.questionbank.dto.response;

import com.mockwise.questionbank.enums.Difficulty;
import com.mockwise.questionbank.enums.Domain;
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
public class CoreQuestionResponse {

    String id;
    QuestionType type;
    Difficulty difficulty;
    QuestionStatus status;
    List<String> tags;
    String text;
    List<String> targetRoles;
    Domain domain;
    List<String> keyConcepts;
    String depthExpected;
    String audioKey;
    String createdBy;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;
}
