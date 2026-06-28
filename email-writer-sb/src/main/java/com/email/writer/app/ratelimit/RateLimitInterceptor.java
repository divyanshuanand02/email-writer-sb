package com.email.writer.app.ratelimit;

import com.email.writer.app.services.RateLimitingService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Enforces the per-IP rate limit before a request reaches the controller.
 * Rejected requests never call Gemini, so they consume no API tokens.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitingService rateLimitingService;

    public RateLimitInterceptor(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        String clientKey = extractClientIp(request);
        Bucket bucket = rateLimitingService.resolveBucket(clientKey);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            return true; // proceed to the controller
        }

        long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // 429
        response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitSeconds));
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"Too many requests. Retry in " + waitSeconds + " seconds.\"}");
        return false; // short-circuit: controller (and Gemini) never run
    }

    /**
     * Prefer X-Forwarded-For (set by proxies/load balancers once deployed),
     * otherwise fall back to the direct socket address.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
