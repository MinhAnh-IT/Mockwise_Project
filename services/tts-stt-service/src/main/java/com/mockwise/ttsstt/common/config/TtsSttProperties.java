package com.mockwise.ttsstt.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tts-stt")
public record TtsSttProperties(
        Ffmpeg ffmpeg,
        Tts tts,
        Stt stt,
        KafkaTopics kafka
) {
    public record Ffmpeg(String bin, String tmpDir) {}
    public record Tts(int maxTextLength, long cacheTtlSeconds) {}
    public record Stt(long maxVideoSizeBytes, int maxDurationSeconds, int maxAttempts, int processingTimeoutMinutes) {}
    public record KafkaTopics(String topicAnswerSubmitted, String topicTranscriptReady, String topicTranscriptFailed) {}
}
