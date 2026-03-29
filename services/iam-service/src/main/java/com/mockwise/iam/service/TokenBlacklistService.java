package com.mockwise.iam.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TokenBlacklistService {
    StringRedisTemplate redisTemplate;

    public void blacklist(String jti, long seconds) {
        String key = buildKey(jti);
        redisTemplate.opsForValue().set(key, "", seconds, TimeUnit.SECONDS);
    }

    public boolean isBlacklisted(String jti) {
        return redisTemplate.hasKey(buildKey(jti));
    }

    private String buildKey(String jti) {
        return "blacklist:" + jti;
    }
}
