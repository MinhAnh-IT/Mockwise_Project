package com.interview.judge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FunctionMeta implements Serializable {

    String fn;

    List<ParamMeta> params;

    @JsonProperty("return")
    String returnType;

    boolean orderMatters;

    boolean inPlace;
}
