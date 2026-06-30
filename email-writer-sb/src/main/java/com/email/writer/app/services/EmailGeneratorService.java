package com.email.writer.app.services;

import com.email.writer.app.model.EmailRequest;
import com.email.writer.app.model.TokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class EmailGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(EmailGeneratorService.class);

    // ObjectMapper is thread-safe; reuse a single instance instead of allocating per call.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final WebClient webClient;
    private final CostEstimator costEstimator;
    private final UsageTracker usageTracker;

    public EmailGeneratorService(WebClient.Builder webClientBuilder,
                                 CostEstimator costEstimator,
                                 UsageTracker usageTracker) {
        this.webClient = webClientBuilder.build();
        this.costEstimator = costEstimator;
        this.usageTracker = usageTracker;
    }

    public String generateEmailReply(EmailRequest emailRequest) {
        log.debug("generateEmailReply called");
        String prompt = buildPrompt(emailRequest);

        Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", prompt)
                        })
                }
        );

        String response = webClient.post()
                .uri(geminiApiUrl + geminiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return handleResponse(response);
    }

    /**
     * Parses the Gemini response once: extracts the reply text and the token
     * usage, records usage (so a future cache hit that never reaches here costs
     * zero tokens — see Phase 3), and returns the reply text.
     */
    private String handleResponse(String response) {
        JsonNode root;
        try {
            root = MAPPER.readTree(response);
        } catch (Exception e) {
            log.warn("Failed to parse Gemini response", e);
            usageTracker.record(TokenUsage.ZERO);
            return "Error processing request: " + e.getMessage();
        }

        TokenUsage usage = extractUsage(root);
        usageTracker.record(usage);
        log.info("Gemini usage prompt={} completion={} total={} estCost=${}",
                usage.promptTokens(), usage.completionTokens(),
                usage.totalTokens(), String.format("%.6f", usage.estimatedCostUsd()));

        return extractReplyText(root);
    }

    String extractReplyText(JsonNode root) {
        try {
            return root.path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text")
                    .asText();
        } catch (Exception e) {
            log.warn("Failed to extract reply text from Gemini response", e);
            return "Error processing request: " + e.getMessage();
        }
    }

    /**
     * Reads {@code usageMetadata}, defaulting each field to 0 when absent
     * (Gemini omits it on error / safety-blocked responses).
     */
    TokenUsage extractUsage(JsonNode root) {
        JsonNode meta = root.path("usageMetadata");
        int promptTokens = meta.path("promptTokenCount").asInt(0);
        int completionTokens = meta.path("candidatesTokenCount").asInt(0);
        int totalTokens = meta.path("totalTokenCount").asInt(promptTokens + completionTokens);
        double cost = costEstimator.estimate(promptTokens, completionTokens);
        return new TokenUsage(promptTokens, completionTokens, totalTokens, cost);
    }

    private String buildPrompt(EmailRequest emailRequest) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate a professional email reply for the email content. Please don't generate a subject line. ");

        if (emailRequest.getTone() != null && !emailRequest.getTone().isEmpty()) {
            prompt.append("Use a ").append(emailRequest.getTone()).append(" tone. ");
        }

        prompt.append("\nOriginal email:\n")
                .append(emailRequest.getEmailContent());

        return prompt.toString();
    }
}
