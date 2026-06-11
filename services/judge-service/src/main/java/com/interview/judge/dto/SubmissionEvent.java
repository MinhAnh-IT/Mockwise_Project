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
public class SubmissionEvent {
    UUID submissionId;
    /**
     * Which feature produced this submission: {@code "INTERVIEW"} or
     * {@code "PRACTICE"}. Nullable for backward compatibility — a missing
     * value is treated as INTERVIEW (the original, sole producer). Echoed
     * back on {@link JudgeResultEvent} so each consumer group can fast-filter
     * verdicts that belong to it.
     */
    String origin;
    String language;
    String code;
    FunctionMeta functionMeta;
    List<TestCaseDto> testCases;
}
