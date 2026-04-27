package com.mockwise.questionbank.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StarterCode {
    String java;
    String python;
    String cpp;
    String javascript;
}
