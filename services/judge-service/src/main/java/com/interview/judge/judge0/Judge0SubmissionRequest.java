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

    @JsonProperty("memory_limit")
    int memoryLimit;
}
