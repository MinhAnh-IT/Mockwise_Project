package com.interview.judge.judge0;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Judge0CallbackPayload {

    String token;

    /** Base64-encoded stdout from the program. */
    String stdout;

    /** Base64-encoded stderr from the program. */
    String stderr;

    /** Base64-encoded compiler error output (populated when status.id == 6). */
    @JsonProperty("compile_output")
    String compileOutput;

    StatusInfo status;

    /** Execution time in seconds, e.g. "0.120". */
    String time;

    /** Memory used in KB. */
    Integer memory;

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class StatusInfo {
        int id;
        String description;
    }
}
