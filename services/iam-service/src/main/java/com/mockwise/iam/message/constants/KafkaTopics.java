package com.mockwise.iam.message.constants;

public class KafkaTopics {
    public static final String EMAIL = "email-topic";

    /**
     * Central admin audit trail. Producers: the API gateway (coarse access audit
     * for every mutating {@code /admin/**} request) and IAM itself (auth events:
     * login / logout). The single consumer lives in IAM ({@code AuditConsumer}),
     * which persists each event to the {@code audit_log} table.
     */
    public static final String AUDIT = "audit-log";
}
