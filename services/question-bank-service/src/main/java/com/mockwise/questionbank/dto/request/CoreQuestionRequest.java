package com.mockwise.questionbank.dto.request;

import com.mockwise.questionbank.enums.Difficulty;
import com.mockwise.questionbank.enums.Domain;
import com.mockwise.questionbank.enums.TargetRole;
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
public class CoreQuestionRequest {

    @NotNull(message = "difficulty is required")
    Difficulty difficulty;

    List<String> tags;

    @NotBlank(message = "text is required")
    String text;

    @NotEmpty(message = "targetRoles must not be empty")
    List<TargetRole> targetRoles;

    @NotNull(message = "domain is required")
    Domain domain;

    @NotEmpty(message = "keyConcepts must not be empty")
    List<String> keyConcepts;

    @NotBlank(message = "depthExpected is required")
    String depthExpected;
}
