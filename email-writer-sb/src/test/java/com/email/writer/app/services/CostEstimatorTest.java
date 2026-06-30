package com.email.writer.app.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CostEstimatorTest {

    @Test
    void pricesInputAndOutputSeparatelyPerMillionTokens() {
        CostEstimator estimator = new CostEstimator(0.10, 0.40);

        // 1M input @ 0.10 + 1M output @ 0.40 = 0.50
        assertEquals(0.50, estimator.estimate(1_000_000, 1_000_000), 1e-12);
    }

    @Test
    void zeroTokensCostNothing() {
        CostEstimator estimator = new CostEstimator(0.10, 0.40);

        assertEquals(0.0, estimator.estimate(0, 0), 1e-12);
    }
}
