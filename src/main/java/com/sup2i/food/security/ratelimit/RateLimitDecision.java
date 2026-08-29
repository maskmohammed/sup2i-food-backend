package com.sup2i.food.security.ratelimit;

public record RateLimitDecision(
    boolean allowed,
    long availableTokens,
    long retryAfterSeconds
) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(
            true,
            Long.MAX_VALUE,
            0L
        );
    }

    public static RateLimitDecision allow(
        long availableTokens
    ) {
        return new RateLimitDecision(
            true,
            availableTokens,
            0L
        );
    }

    public static RateLimitDecision limited(
        long retryAfterSeconds
    ) {
        return new RateLimitDecision(
            false,
            0L,
            retryAfterSeconds
        );
    }
}