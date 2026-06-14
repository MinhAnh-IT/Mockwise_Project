package com.interview.judge.admin;

import com.interview.judge.admin.dto.DlqOverview;
import com.interview.judge.admin.dto.DlqOverview.DlqMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Read-only inspector for the judge dead-letter topic. Builds a short-lived
 * {@link KafkaConsumer} per request (no listener container, no committed group
 * offsets) so a context that can't reach Kafka still starts cleanly and the
 * probe simply reports {@code reachable=false}.
 *
 * <p>Replay/skip is intentionally NOT implemented here yet — this is the
 * read-only first cut; mutating the DLT would be added behind an explicit
 * action later.
 */
@Slf4j
@Service
public class DlqInspectionService {

    private static final int MAX_PREVIEW_CHARS = 2000;

    private final String bootstrapServers;
    private final String dlqTopic;

    public DlqInspectionService(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${judge.dlq.topic:code-submission.DLT}") String dlqTopic) {
        this.bootstrapServers = bootstrapServers;
        this.dlqTopic = dlqTopic;
    }

    public DlqOverview overview(int limit) {
        int cap = Math.min(Math.max(limit, 1), 200);
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(consumerProps())) {
            List<PartitionInfo> partitionInfos = consumer.partitionsFor(dlqTopic, Duration.ofSeconds(5));
            if (partitionInfos == null || partitionInfos.isEmpty()) {
                return new DlqOverview(dlqTopic, true, 0, List.of(),
                        "Topic chưa tồn tại hoặc chưa có message nào.");
            }

            List<TopicPartition> partitions = new ArrayList<>();
            for (PartitionInfo pi : partitionInfos) {
                partitions.add(new TopicPartition(dlqTopic, pi.partition()));
            }
            consumer.assign(partitions);

            Map<TopicPartition, Long> begin = consumer.beginningOffsets(partitions);
            Map<TopicPartition, Long> end = consumer.endOffsets(partitions);

            long total = 0;
            for (TopicPartition tp : partitions) {
                total += end.getOrDefault(tp, 0L) - begin.getOrDefault(tp, 0L);
            }

            // Seek so we read at most `cap` of the newest messages per partition.
            for (TopicPartition tp : partitions) {
                long from = Math.max(begin.getOrDefault(tp, 0L), end.getOrDefault(tp, 0L) - cap);
                consumer.seek(tp, from);
            }

            List<DlqMessage> messages = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 5000;
            while (messages.size() < total && System.currentTimeMillis() < deadline) {
                ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(500));
                if (records.isEmpty()) {
                    break;
                }
                for (ConsumerRecord<byte[], byte[]> record : records) {
                    messages.add(toMessage(record));
                }
            }

            messages.sort(Comparator.comparing(DlqMessage::timestamp,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed());
            if (messages.size() > cap) {
                messages = new ArrayList<>(messages.subList(0, cap));
            }

            return new DlqOverview(dlqTopic, true, total, messages, null);
        } catch (Exception e) {
            log.warn("DLQ inspection failed for topic {}: {}", dlqTopic, e.getMessage());
            return new DlqOverview(dlqTopic, false, 0, List.of(),
                    "Không đọc được DLQ: " + e.getMessage());
        }
    }

    private Properties consumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "judge-dlq-inspector-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 4000);
        return props;
    }

    private DlqMessage toMessage(ConsumerRecord<byte[], byte[]> record) {
        String originalTopic = headerString(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        Integer originalPartition = headerInt(record, KafkaHeaders.DLT_ORIGINAL_PARTITION);
        Long originalOffset = headerLong(record, KafkaHeaders.DLT_ORIGINAL_OFFSET);
        String exClass = headerString(record, KafkaHeaders.DLT_EXCEPTION_FQCN);
        String exMessage = headerString(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);

        String preview = null;
        if (record.value() != null) {
            String body = new String(record.value(), StandardCharsets.UTF_8);
            preview = body.length() > MAX_PREVIEW_CHARS
                    ? body.substring(0, MAX_PREVIEW_CHARS) + "…(truncated)" : body;
        }

        OffsetDateTime ts = record.timestamp() > 0
                ? Instant.ofEpochMilli(record.timestamp()).atOffset(ZoneOffset.UTC) : null;

        return new DlqMessage(
                record.partition(), record.offset(), ts,
                originalTopic, originalPartition, originalOffset,
                exClass, exMessage, preview);
    }

    private static String headerString(ConsumerRecord<byte[], byte[]> record, String key) {
        Header h = record.headers().lastHeader(key);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static Integer headerInt(ConsumerRecord<byte[], byte[]> record, String key) {
        Header h = record.headers().lastHeader(key);
        if (h == null || h.value() == null || h.value().length < 4) return null;
        return ByteBuffer.wrap(h.value()).getInt();
    }

    private static Long headerLong(ConsumerRecord<byte[], byte[]> record, String key) {
        Header h = record.headers().lastHeader(key);
        if (h == null || h.value() == null || h.value().length < 8) return null;
        return ByteBuffer.wrap(h.value()).getLong();
    }
}
