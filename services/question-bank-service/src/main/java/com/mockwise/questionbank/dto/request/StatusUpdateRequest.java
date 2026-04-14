package com.mockwise.questionbank.dto.request;

import com.mockwise.questionbank.enums.QuestionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StatusUpdateRequest {

    @NotNull(message = "status is required")
    QuestionStatus status;
}
