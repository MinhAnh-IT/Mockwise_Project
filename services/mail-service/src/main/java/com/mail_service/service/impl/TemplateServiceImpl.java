package com.mail_service.service.impl;

import com.mail_service.dto.EmailEvent;
import com.mail_service.enums.EmailType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TemplateServiceImpl {

    TemplateEngine templateEngine;

    public String subjectOf(EmailType type) {
        return switch (type) {
            case OTP_VERIFICATION -> "MockWise - Verify your account";
            case RESET_PASSWORD -> "MockWise - Reset your password";
        };
    }

    public String renderHtml(EmailEvent event) {
        String contentTemplate = switch (event.getType()) {
            case OTP_VERIFICATION -> "email/verify-otp";
            case RESET_PASSWORD -> "email/reset-password";
        };

        Context ctx = new Context();
        ctx.setVariable("brand", "MockWise");
        ctx.setVariable("content", event.getContent());
        ctx.setVariable("contentTemplate", contentTemplate);

        return templateEngine.process("email/_base", ctx);
    }
}
