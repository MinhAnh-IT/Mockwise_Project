package com.mockwise.ttsstt.stt.dto.response;

import com.mockwise.ttsstt.stt.entity.Transcript;
import com.mockwise.ttsstt.stt.entity.TranscriptWord;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TranscriptResponse {
    String transcriptId;
    String sttJobId;
    String answerId;
    String sessionId;
    String questionId;
    String text;
    String languageCode;
    Float languageConfidence;
    List<TranscriptWord> words;
    Integer durationMs;
    String modelId;
    OffsetDateTime createdAt;

    public static TranscriptResponse from(Transcript t) {
        return TranscriptResponse.builder()
                .transcriptId(t.getId())
                .sttJobId(t.getSttJobId())
                .answerId(t.getAnswerId())
                .sessionId(t.getSessionId())
                .questionId(t.getQuestionId())
                .text(t.getText())
                .languageCode(t.getLanguageCode())
                .languageConfidence(t.getLanguageConfidence())
                .words(t.getWords())
                .durationMs(t.getDurationMs())
                .modelId(t.getModelId())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
