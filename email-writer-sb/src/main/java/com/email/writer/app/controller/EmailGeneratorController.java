package com.email.writer.app.controller;

import com.email.writer.app.services.EmailGeneratorService;
import com.email.writer.app.services.UsageTracker;
import com.email.writer.app.model.EmailRequest;
import lombok.AllArgsConstructor;
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
}
