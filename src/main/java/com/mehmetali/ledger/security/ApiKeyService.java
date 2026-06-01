package com.mehmetali.ledger.security;

import com.mehmetali.ledger.domain.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String CACHE_PREFIX = "apikey:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final ApiKeyRepository apiKeyRepository;
    private final StringRedisTemplate redisTemplate;

    public boolean isValid(String rawKey) {
        String hash = sha256(rawKey);
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + hash);
        if (cached != null) {
            return true;
        }
        boolean exists = apiKeyRepository.findByKeyHashAndActiveTrue(hash).isPresent();
        if (exists) {
            redisTemplate.opsForValue().set(CACHE_PREFIX + hash, "1", CACHE_TTL);
        }
        return exists;
    }

    public String hash(String rawKey) {
        return sha256(rawKey);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
