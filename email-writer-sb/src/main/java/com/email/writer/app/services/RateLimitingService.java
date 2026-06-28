package com.email.writer.app.services;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands out one token bucket per client key (IP address). Buckets are created
 * lazily on first request and cached in a thread-safe map so each client gets
 * its own independent quota.
 */
@Service
public class RateLimitingService {

    @Value("${ratelimit.capacity}")
    private long capacity;

    @Value("${ratelimit.refill-tokens}")
    private long refillTokens;

    @Value("${ratelimit.refill-duration-minutes}")
    private long refillMinutes;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String clientKey) {
        return buckets.computeIfAbsent(clientKey, k -> newBucket());
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(refillTokens, Duration.ofMinutes(refillMinutes))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
