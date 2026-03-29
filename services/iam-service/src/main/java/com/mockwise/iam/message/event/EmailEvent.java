package com.mockwise.iam.message.event;

import com.mockwise.iam.message.enums.EmailType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailEvent {
    String to;
    String content;
    EmailType type;
}
