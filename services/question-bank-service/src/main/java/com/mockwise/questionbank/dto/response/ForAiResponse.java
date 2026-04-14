package com.mockwise.questionbank.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ForAiResponse {

    @JsonProperty("interview_type")
    String interviewType;

    Map<String, Object> question;

    // ── Static factory helpers ──────────────────────────────────────────────

    public static ForAiResponse fromBehavioral(String id, String text,
                                                String competency, List<String> expectedSignals) {
        return ForAiResponse.builder()
                .interviewType("behavioral")
                .question(Map.of(
                        "id", id,
                        "text", text,
                        "competency", competency.toLowerCase(),
                        "expected_signals", expectedSignals
                ))
                .build();
    }

    public static ForAiResponse fromCore(String id, String text, String domain,
                                          List<String> keyConcepts, String depthExpected) {
        return ForAiResponse.builder()
                .interviewType("core_conceptual")
                .question(Map.of(
                        "id", id,
                        "text", text,
                        "domain", domain.toLowerCase(),
                        "key_concepts", keyConcepts,
                        "depth_expected", depthExpected
                ))
                .build();
    }

    public static ForAiResponse fromCoding(String id, String title, String description,
                                            String difficulty, List<String> tags,
                                            int timeLimitMinutes, String optimalTime, String optimalSpace) {
        return ForAiResponse.builder()
                .interviewType("live_coding")
                .question(Map.of(
                        "id", id,
                        "title", title,
                        "description", description,
                        "difficulty", difficulty.toLowerCase(),
                        "tags", tags,
                        "time_limit_minutes", timeLimitMinutes,
                        "optimal_complexity", Map.of("time", optimalTime, "space", optimalSpace)
                ))
                .build();
    }
}
