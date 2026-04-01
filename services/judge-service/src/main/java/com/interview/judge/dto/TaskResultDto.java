package com.interview.judge.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TaskResultDto {
    UUID testCaseId;
    String status;
    String stdout;
    String stderr;
    Integer runtimeMs;
    Integer memoryKb;
}
