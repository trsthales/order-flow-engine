package br.com.trsthales.orderflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private static final String KEY_PREFIX = "idempotency:orders:";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<UUID> tryAcquire(String key) {
        String redisKey = KEY_PREFIX + key;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, IN_PROGRESS, TTL);

        if (Boolean.TRUE.equals(acquired)) {
            return Optional.empty();
        }

        String currentValue = redisTemplate.opsForValue().get(redisKey);
        if (currentValue == null || IN_PROGRESS.equals(currentValue)) {
            log.warn("Concurrent request detected for idempotency key: {}", key);
            throw new IdempotencyConflictException("Request with this Idempotency-Key is already being processed");
        }

        log.info("Idempotency hit for key: {}, returning existing order: {}", key, currentValue);
        return Optional.of(UUID.fromString(currentValue));
    }

    public void markCompleted(String key, UUID orderId) {
        redisTemplate.opsForValue().set(KEY_PREFIX + key, orderId.toString(), TTL);
    }

    public void release(String key) {
        redisTemplate.delete(KEY_PREFIX + key);
    }
}