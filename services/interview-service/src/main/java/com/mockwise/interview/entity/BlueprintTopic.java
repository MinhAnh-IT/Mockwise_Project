package com.mockwise.interview.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mockwise.interview.enums.Difficulty;
import com.mockwise.interview.enums.Importance;
import com.mockwise.interview.enums.TopicKind;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * One topic entry inside an {@link InterviewBlueprint#topics} JSONB array.
 *
 * <p>{@code topicValue} is intentionally a String — it must hold either a
 * {@code Competency} or {@code Domain} enum name from question-bank, and
 * we don't want a Java-level coupling to question-bank's enums in this
 * service. Validation that the string matches a real bank enum happens at
 * blueprint load time, not at JSON parse time, so admin tooling can stage
 * blueprints that reference future bank values.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} means a future
 * field added by admins (e.g. {@code description}) will not break old
 * services that haven't redeployed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BlueprintTopic {

    TopicKind kind;
    String topicValue;
    Importance importance;
    Difficulty targetDifficulty;
    int orderHint;
}
