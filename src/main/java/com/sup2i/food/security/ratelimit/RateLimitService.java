package com.sup2i.food.security.ratelimit;

import com.sup2i.food.security.config.SecurityProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final SecurityProperties properties;
    private final ConcurrentHashMap<String, Bucket> buckets =
        new ConcurrentHashMap<>();

    public RateLimitService(
        SecurityProperties properties
    ) {
        this.properties = properties;
    }

    public RateLimitDecision tryConsume(
        String bucketName,
        String key
    ) {
        SecurityProperties.RateLimit config =
            properties.rateLimit();

        if (
            config == null
            || !config.enabled()
        ) {
            return RateLimitDecision.allow();
        }

        if (bucketName == null
            || bucketName.isBlank()
            || key == null
            || key.isBlank()) {
            return RateLimitDecision.allow();
        }

        SecurityProperties.RateLimit.Bucket definition =
            config.buckets() == null
                ? null
                : config.buckets()
                    .stream()
                    .filter(candidate ->
                        bucketName.equals(
                            candidate.name()
                        )
                    )
                    .findFirst()
                    .orElse(null);

        if (definition == null) {
            return RateLimitDecision.allow();
        }

        String cacheKey =
            bucketName + "|" + key;

        Bucket bucket =
            buckets.computeIfAbsent(
                cacheKey,
                ignored ->
                    buildBucket(definition)
            );

        if (bucket.tryConsume(1)) {
            return RateLimitDecision.allow(
                bucket.getAvailableTokens()
            );
        }

        return RateLimitDecision.limited(
            retryAfterSeconds(definition)
        );
    }

    public void reset() {
        buckets.clear();
    }

    private Bucket buildBucket(
        SecurityProperties.RateLimit.Bucket definition
    ) {
        int capacity =
            Math.max(definition.capacity(), 1);

        int tokens =
            Math.max(
                definition.refillTokens(),
                1
            );

        Duration period =
            definition.refillPeriod();

        if (
            period == null
            || period.isZero()
            || period.isNegative()
        ) {
            period = Duration.ofMinutes(1);
        }

        Bandwidth limit = Bandwidth.classic(
            capacity,
            Refill.greedy(tokens, period)
        );

        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    private long retryAfterSeconds(
        SecurityProperties.RateLimit.Bucket definition
    ) {
        long periodSeconds =
            definition.refillPeriod() == null
                ? 60L
                : Math.max(
                    definition.refillPeriod()
                        .toSeconds(),
                    1L
                );

        int tokensPerPeriod =
            Math.max(
                definition.refillTokens(),
                1
            );

        return Math.max(
            1L,
            (periodSeconds + tokensPerPeriod - 1)
                / tokensPerPeriod
        );
    }
}