package com.mockwise.questionbank.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FunctionMeta {

    String fn;
    List<ParamMeta> params;

    @JsonProperty("return")
    String returnType;

    boolean orderMatters;
    boolean inPlace;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ParamMeta {
        String name;
        String type;
    }
}
