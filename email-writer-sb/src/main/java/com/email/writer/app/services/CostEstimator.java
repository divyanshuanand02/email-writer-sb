package com.email.writer.app.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Estimates the USD cost of a Gemini call from its token counts. Input and
 * output tokens are priced separately, matching Gemini's published pricing.
 * Rates are configured in USD per 1,000,000 tokens (the same unit Gemini uses).
 *
 * <p>These are estimates derived from list pricing, not billed amounts. Verify
 * {@code gemini.cost.*} against current pricing before relying on the numbers.
 */
@Component
public class CostEstimator {

    private static final double TOKENS_PER_UNIT = 1_000_000.0;

    private final double inputRatePerMillion;
    private final double outputRatePerMillion;

    public CostEstimator(
            @Value("${gemini.cost.input-per-1m:0.10}") double inputRatePerMillion,
            @Value("${gemini.cost.output-per-1m:0.40}") double outputRatePerMillion) {
        this.inputRatePerMillion = inputRatePerMillion;
        this.outputRatePerMillion = outputRatePerMillion;
    }

    public double estimate(int promptTokens, int completionTokens) {
        double inputCost = (promptTokens / TOKENS_PER_UNIT) * inputRatePerMillion;
        double outputCost = (completionTokens / TOKENS_PER_UNIT) * outputRatePerMillion;
        return inputCost + outputCost;
    }
}
