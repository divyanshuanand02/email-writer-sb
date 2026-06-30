package com.email.writer.app.services;

import com.email.writer.app.model.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailGeneratorServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EmailGeneratorService service = new EmailGeneratorService(
            WebClient.builder(),
            new CostEstimator(0.10, 0.40),
            new UsageTracker());

    private JsonNode tree(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @Test
    void extractsTokenCountsFromUsageMetadata() throws Exception {
        String json = """
                {
                  "candidates": [ { "content": { "parts": [ { "text": "Hello back" } ] } } ],
                  "usageMetadata": {
                    "promptTokenCount": 12,
                    "candidatesTokenCount": 34,
                    "totalTokenCount": 46
                  }
                }
                """;

        TokenUsage usage = service.extractUsage(tree(json));

        assertEquals(12, usage.promptTokens());
        assertEquals(34, usage.completionTokens());
        assertEquals(46, usage.totalTokens());
        // 12/1e6 * 0.10 + 34/1e6 * 0.40 = 0.0000012 + 0.0000136 = 0.0000148
        assertEquals(0.0000148, usage.estimatedCostUsd(), 1e-12);
    }

    @Test
    void extractsReplyText() throws Exception {
        String json = """
                {
                  "candidates": [ { "content": { "parts": [ { "text": "Hello back" } ] } } ]
                }
                """;

        assertEquals("Hello back", service.extractReplyText(tree(json)));
    }

    @Test
    void defaultsToZeroWhenUsageMetadataMissing() throws Exception {
        String json = """
                {
                  "candidates": [ { "content": { "parts": [ { "text": "x" } ] } } ]
                }
                """;

        TokenUsage usage = service.extractUsage(tree(json));

        assertEquals(0, usage.promptTokens());
        assertEquals(0, usage.completionTokens());
        assertEquals(0, usage.totalTokens());
        assertEquals(0.0, usage.estimatedCostUsd(), 1e-12);
    }

    @Test
    void totalFallsBackToSumWhenTotalMissing() throws Exception {
        String json = """
                {
                  "usageMetadata": {
                    "promptTokenCount": 10,
                    "candidatesTokenCount": 5
                  }
                }
                """;

        TokenUsage usage = service.extractUsage(tree(json));

        assertEquals(15, usage.totalTokens());
    }
}
