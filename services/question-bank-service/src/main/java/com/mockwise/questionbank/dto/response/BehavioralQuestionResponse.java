package com.mockwise.questionbank.dto.response;

import com.mockwise.questionbank.enums.Competency;
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
public class BehavioralQuestionResponse {

    String id;
    QuestionType type;
    Difficulty difficulty;
    QuestionStatus status;
    List<String> tags;
    String text;
    Competency competency;
    List<String> expectedSignals;
    String audioKey;
    String createdBy;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;
}
