package com.interview.judge.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Makes the {@code code-submission} consumer resilient to un-parseable
 * messages.
 *
 * <p>Before this, the consumer used a bare {@code JsonDeserializer}. A single
 * message that could not be deserialized — e.g. a {@code testCases[].id} that
 * is not a canonical UUID ({@code TestCaseDto.id} is {@link java.util.UUID}) —
 * threw inside the Kafka poll loop on every retry, never advanced the offset,
 * and blocked <em>every</em> subsequent submission (a classic poison pill).
 * That is exactly how prod wedged: judge stuck on offset 0, no submission
 * judged, nothing AI-scored, and the retry log grew until the disk filled.
 *
 * <p>With {@code ErrorHandlingDeserializer} (wired in {@code application.yml})
 * a parse failure becomes a failed record instead of a thrown exception. This
 * {@link DefaultErrorHandler} then routes it to a dead-letter topic
 * ({@code <topic>.DLT}) so the consumer keeps making progress and the bad
 * payload stays inspectable. Deserialization errors are deterministic, so
 * they are not retried — they go straight to the DLT.
 *
 * <p>Spring Boot's Kafka auto-configuration detects the single
 * {@link DefaultErrorHandler} bean and installs it on the default listener
 * container factory, so no custom factory is needed.
 *
 * <p>Note: the DLT producer template is built as a <em>local</em> object, not
 * a {@code @Bean}. Exposing a {@code KafkaTemplate} bean would trip Boot's
 * {@code @ConditionalOnMissingBean(KafkaTemplate.class)} and suppress the
 * auto-configured default template that {@code JudgeResultProducer} depends
 * on (that broke 21 context-loading tests).
 */
@Slf4j
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {

        // Dedicated byte[]/byte[] producer for the DLT: the recoverer
        // republishes the original raw message bytes (extracted from the
        // DeserializationException), so it must use a ByteArraySerializer —
        // the app's JSON producer would re-serialize and lose the payload.
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        ProducerFactory<byte[], byte[]> dltProducerFactory = new DefaultKafkaProducerFactory<>(props);
        KafkaTemplate<byte[], byte[]> dltTemplate = new KafkaTemplate<>(dltProducerFactory);

        // Send to "<originalTopic>.DLT", partition -1 so the broker assigns one
        // (the DLT may have a different partition count than the source topic).
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltTemplate,
                (record, ex) -> {
                    log.error("Routing un-processable message from {}-{}@{} to DLT: {}",
                            record.topic(), record.partition(), record.offset(),
                            ex.getMessage());
                    return new TopicPartition(record.topic() + ".DLT", -1);
                });

        // Transient listener errors: a couple of retries with back-off.
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 2));
        // A bad payload will never parse on retry — skip straight to the DLT.
        handler.addNotRetryableExceptions(DeserializationException.class);
        return handler;
    }
}
