package com.zoee.equipops.order.service;

import com.zoee.equipops.common.exception.BizException;
import com.zoee.equipops.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class OrderIdempotencyService {

    private static final String KEY_PREFIX = "idempotency:order:create:";
    private static final String PROCESSING = "PROCESSING:";
    private static final String DONE = "DONE:";
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(2);
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final DefaultRedisScript<Long> DELETE_IF_OWNED =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] "
                            + "then return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;

    public OrderIdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Claim begin(Long userId, String idempotencyKey, String requestHash) {
        String redisKey = redisKey(userId, idempotencyKey);
        String ownerToken = UUID.randomUUID().toString();
        String processingValue = processingValue(requestHash, ownerToken);
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, processingValue, PROCESSING_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                return new Claim(redisKey, requestHash, ownerToken, null, true);
            }

            String existing = redisTemplate.opsForValue().get(redisKey);
            ParsedValue parsed = parse(existing);
            if (parsed != null && !requestHash.equals(parsed.requestHash())) {
                throw new BizException(
                        ResultCode.ORDER_IDEMPOTENCY_CONFLICT,
                        "同一 Idempotency-Key 不能用于不同请求内容"
                );
            }
            Long completedOrderId = parsed != null && parsed.done() ? parsed.orderId() : null;
            return new Claim(redisKey, requestHash, ownerToken, completedOrderId, false);
        } catch (DataAccessException exception) {
            // Redis 只是快路径。故障时继续走数据库唯一索引，不能让缓存决定正确性。
            log.warn("Redis idempotency fast path unavailable, fallback to database, userId={}",
                    userId, exception);
            return new Claim(redisKey, requestHash, ownerToken, null, false);
        }
    }

    public void completeAfterCommit(Claim claim, Long orderId) {
        Runnable complete = () -> writeCompleted(claim, orderId);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    complete.run();
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED && claim.acquired()) {
                        release(claim);
                    }
                }
            });
            return;
        }
        complete.run();
    }

    public void release(Claim claim) {
        if (!claim.acquired()) {
            return;
        }
        try {
            redisTemplate.execute(
                    DELETE_IF_OWNED,
                    List.of(claim.redisKey()),
                    processingValue(claim.requestHash(), claim.ownerToken())
            );
        } catch (DataAccessException exception) {
            log.warn("Failed to release idempotency marker, key={}", claim.redisKey(), exception);
        }
    }

    String redisKey(Long userId, String idempotencyKey) {
        return KEY_PREFIX + userId + ":" + sha256(idempotencyKey);
    }

    private void writeCompleted(Claim claim, Long orderId) {
        try {
            redisTemplate.opsForValue().set(
                    claim.redisKey(),
                    DONE + claim.requestHash() + ":" + orderId,
                    RESULT_TTL
            );
        } catch (DataAccessException exception) {
            log.warn("Failed to cache idempotency result, orderId={}", orderId, exception);
        }
    }

    private ParsedValue parse(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":", 3);
        if (parts.length < 2) {
            return null;
        }
        if ("PROCESSING".equals(parts[0])) {
            return new ParsedValue(false, parts[1], null);
        }
        if ("DONE".equals(parts[0]) && parts.length == 3) {
            try {
                return new ParsedValue(true, parts[1], Long.valueOf(parts[2]));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String processingValue(String requestHash, String ownerToken) {
        return PROCESSING + requestHash + ":" + ownerToken;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 不支持 SHA-256", exception);
        }
    }

    public record Claim(
            String redisKey,
            String requestHash,
            String ownerToken,
            Long completedOrderId,
            boolean acquired
    ) {
    }

    private record ParsedValue(boolean done, String requestHash, Long orderId) {
    }
}
