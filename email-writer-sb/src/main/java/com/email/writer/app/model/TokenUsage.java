package com.email.writer.app.model;

/**
 * Token usage for a single Gemini call, parsed from the response's
 * {@code usageMetadata}. {@code estimatedCostUsd} is derived from published
 * per-token pricing and is an estimate, not a billed amount.
 */
public record TokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        double estimatedCostUsd
) {
    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0, 0.0);
}
