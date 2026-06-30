package com.email.writer.app.services;

import com.email.writer.app.model.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Accumulates token usage and estimated cost across all Gemini calls for the
 * lifetime of the process. Thread-safe; in-memory only, so totals reset on
 * restart.
 *
 * <p>TODO(Phase 3): once Redis is wired in, totals could be persisted there to
 * survive restarts and aggregate across instances.
 */
@Component
public class UsageTracker {

    private final AtomicLong requestCount = new AtomicLong();
    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();
    private final AtomicLong totalTokens = new AtomicLong();
    private final DoubleAdder costUsd = new DoubleAdder();

    public void record(TokenUsage usage) {
        requestCount.incrementAndGet();
        promptTokens.addAndGet(usage.promptTokens());
        completionTokens.addAndGet(usage.completionTokens());
        totalTokens.addAndGet(usage.totalTokens());
        costUsd.add(usage.estimatedCostUsd());
    }

    public UsageSnapshot snapshot() {
        return new UsageSnapshot(
                requestCount.get(),
                promptTokens.get(),
                completionTokens.get(),
                totalTokens.get(),
                costUsd.sum()
        );
    }

    /** Immutable point-in-time view of cumulative usage, serialized by the API. */
    public record UsageSnapshot(
            long requestCount,
            long promptTokens,
            long completionTokens,
            long totalTokens,
            double estimatedCostUsd
    ) {}
}
