package com.mail_service.service.impl;

import com.mail_service.dto.EmailEvent;
import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MailServiceImpl {

    final JavaMailSender mailSender;
    final TemplateServiceImpl templateService;

    @Value("${spring.mail.username:}")
    String mailUsername;

    @Value("${spring.mail.password:}")
    String mailPassword;

    @Value("${spring.mail.from-name:MockWise}")
    String mailFromName;

    public void sendHtml(EmailEvent event) {
        if (mailUsername.isBlank() || mailPassword.isBlank()) {
            log.warn("Skip sending email: MAIL_USERNAME or MAIL_PASSWORD is not configured. type={}, to={}",
                    event.getType(), event.getTo());
            return;
        }

        try {
            String html = templateService.renderHtml(event);
            String subject = templateService.subjectOf(event.getType());

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(mailUsername, mailFromName);
            helper.setTo(event.getTo());
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(mimeMessage);
            log.info("Email sent successfully: type={}, to={}", event.getType(), event.getTo());
        } catch (Exception e) {
            log.error("Failed to send email: type={}, to={}, error={}", event.getType(), event.getTo(), e.getMessage(), e);
            throw new RuntimeException("Failed to send email to " + event.getTo(), e);
        }
    }
}
