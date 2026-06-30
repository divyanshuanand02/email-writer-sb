package com.email.writer.app.controller;

import com.email.writer.app.exception.GeminiUnavailableException;
import com.email.writer.app.services.EmailGeneratorService;
import com.email.writer.app.services.UsageTracker;
import com.email.writer.app.model.EmailRequest;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/email")
@AllArgsConstructor
public class EmailGeneratorController {
    private final EmailGeneratorService emailGeneratorService;
    private final UsageTracker usageTracker;

    @PostMapping("/generate")
    public ResponseEntity<String> generateEmail(@RequestBody EmailRequest emailRequest) {
        String response = emailGeneratorService.generateEmailReply(emailRequest);
        return ResponseEntity.ok(response);
    }

    /** Cumulative token usage and estimated cost since the process started. */
    @GetMapping("/usage")
    public ResponseEntity<UsageTracker.UsageSnapshot> usage() {
        return ResponseEntity.ok(usageTracker.snapshot());
    }

    /** Translate a failed Gemini call into a clean 502 instead of a raw 500. */
    @ExceptionHandler(GeminiUnavailableException.class)
    public ResponseEntity<String> handleGeminiUnavailable(GeminiUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("The AI service is currently unavailable. Please try again later.");
    }
}
