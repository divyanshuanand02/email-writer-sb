package com.email.writer.app.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import io.github.bucket4j.Bucket;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimiterConfig {

    @Bean
    public Bucket bucket() {

        Bandwidth limit = Bandwidth.classic(
                10,
                Refill.intervally(10, Duration.ofMinutes(1))
        );

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}