package com.mockwise.ttsstt.tts.service;

import com.mockwise.ttsstt.common.config.TtsSttProperties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Redis-backed cache for TTS results. The mapping (text + voice + model) → audio
 * pointer is purely an optimization: losing it costs one ElevenLabs call, never
 * data correctness. Source of truth for which audio belongs to which question is
 * still question-bank's audio_key column.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TtsCacheService {

    private static final String KEY_PREFIX = "tts:cache:v1:";

    RedisTemplate<String, Object> redis;
    TtsSttProperties props;

    public String hash(String text, String voiceId, String modelId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(text.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(voiceId.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(modelId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public TtsCacheEntry get(String contentHash) {
        Object v = redis.opsForValue().get(KEY_PREFIX + contentHash);
        if (v instanceof TtsCacheEntry e) return e;
        return null;
    }

    public void put(String contentHash, TtsCacheEntry entry) {
        Duration ttl = Duration.ofSeconds(props.tts().cacheTtlSeconds());
        try {
            redis.opsForValue().set(KEY_PREFIX + contentHash, entry, ttl);
        } catch (Exception ex) {
            log.warn("Failed to write tts cache for hash={} — continuing without cache", contentHash, ex);
        }
    }
}
