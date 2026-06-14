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
    /**
     * Throwaway run: the result is polled once and discarded (admin "Kiểm tra
     * đề" validation). Ephemeral jobs skip the verdict-topic publish and are
     * purged shortly after. Defaults to {@code false} (a normal, persisted job).
     */
    boolean ephemeral;
    String language;
    String code;
    FunctionMeta functionMeta;
    List<TestCaseDto> testCases;
}
