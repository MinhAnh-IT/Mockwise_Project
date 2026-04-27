package com.mockwise.ttsstt.stt.kafka;

import com.mockwise.ttsstt.common.config.TtsSttProperties;
import com.mockwise.ttsstt.stt.kafka.event.TranscriptFailedEvent;
import com.mockwise.ttsstt.stt.kafka.event.TranscriptReadyEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TranscriptEventProducer {

    KafkaTemplate<String, Object> kafkaTemplate;
    TtsSttProperties props;

    public void publishReady(TranscriptReadyEvent event) {
        kafkaTemplate.send(props.kafka().topicTranscriptReady(), event.getAnswerId(), event);
        log.info("Published transcript-ready: answerId={} transcriptId={}",
                event.getAnswerId(), event.getTranscriptId());
    }

    public void publishFailed(TranscriptFailedEvent event) {
        kafkaTemplate.send(props.kafka().topicTranscriptFailed(), event.getAnswerId(), event);
        log.info("Published transcript-failed: answerId={} errorCode={}",
                event.getAnswerId(), event.getErrorCode());
    }
}
