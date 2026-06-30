package com.email.writer.app.exception;

/**
 * Thrown when the Gemini API call fails (non-2xx response, network error, etc.).
 * Surfaced by the controller as HTTP 502 Bad Gateway rather than a raw 500.
 */
public class GeminiUnavailableException extends RuntimeException {
    public GeminiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
