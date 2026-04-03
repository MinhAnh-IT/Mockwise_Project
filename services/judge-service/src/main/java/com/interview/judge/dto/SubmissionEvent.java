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
    String language;
    String code;
    FunctionMeta functionMeta;
    List<TestCaseDto> testCases;
}
