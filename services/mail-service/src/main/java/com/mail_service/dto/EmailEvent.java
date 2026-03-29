package com.mail_service.dto;

import com.mail_service.enums.EmailType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailEvent {
    String to;
    String content;
    EmailType type;
}
