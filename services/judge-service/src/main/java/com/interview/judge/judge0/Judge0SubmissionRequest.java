package com.interview.judge.judge0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Judge0SubmissionRequest {

    @JsonProperty("source_code")
    String sourceCode;

    @JsonProperty("language_id")
    int languageId;

    String stdin;

    @JsonProperty("callback_url")
    String callbackUrl;

    @JsonProperty("cpu_time_limit")
    double cpuTimeLimit;

    // Wall-clock cap for the WHOLE batch (all cases share one process). Sent
    // explicitly so we don't fall back to Judge0's 10s default, which a multi-
    // case batch can exceed under load → spurious whole-batch TLE.
    @JsonProperty("wall_time_limit")
    double wallTimeLimit;

    @JsonProperty("memory_limit")
    int memoryLimit;
}
