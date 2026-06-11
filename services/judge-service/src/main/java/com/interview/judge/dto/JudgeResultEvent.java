package com.interview.judge.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JudgeResultEvent {
    UUID submissionId;
    /** Echoed from {@link SubmissionEvent#getOrigin()} so practice/interview consumers can filter. */
    String origin;
    String verdict;
    List<TaskResultDto> results;
}
