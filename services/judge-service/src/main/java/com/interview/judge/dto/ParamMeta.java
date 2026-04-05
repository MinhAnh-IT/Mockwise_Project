package com.interview.judge.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.io.Serializable;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ParamMeta implements Serializable {
    String name;
    String type;
}
