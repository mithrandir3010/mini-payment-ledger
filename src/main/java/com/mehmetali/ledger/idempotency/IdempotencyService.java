package com.mehmetali.ledger.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public Optional<String> getCachedResponse(String key) {
        String value = redisTemplate.opsForValue().get(PREFIX + key);
        return Optional.ofNullable(value);
    }

    public void saveResponse(String key, String responseJson) {
        redisTemplate.opsForValue().set(PREFIX + key, responseJson, TTL);
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + key));
    }
}
