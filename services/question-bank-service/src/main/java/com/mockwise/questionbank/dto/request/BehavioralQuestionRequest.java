package com.mockwise.questionbank.dto.request;

import com.mockwise.questionbank.enums.Competency;
import com.mockwise.questionbank.enums.Difficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BehavioralQuestionRequest {

    @NotNull(message = "difficulty is required")
    Difficulty difficulty;

    List<String> tags;

    @NotBlank(message = "text is required")
    String text;

    @NotNull(message = "competency is required")
    Competency competency;

    @NotEmpty(message = "expectedSignals must not be empty")
    List<String> expectedSignals;
}
