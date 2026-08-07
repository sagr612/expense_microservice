package com.greatest.gateway;


import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RateLimiterConfig {

    @Bean("authRateLimiter")
    public RedisRateLimiter authRateLimiter() {
        // very strict — brute-force protection on login/signup
        // 1 req/sec, burst 5, 1 token per request
        return new RedisRateLimiter(1, 5, 1);
    }

    @Bean("readRateLimiter")
    @Primary  // default when no @Qualifier
    public RedisRateLimiter readRateLimiter() {
        // reads are cheap — be generous
        // 10 req/sec, burst 50
        return new RedisRateLimiter(10, 50, 1);
    }

    @Bean("writeRateLimiter")
    public RedisRateLimiter writeRateLimiter() {
        // writes are more expensive — mid-tier
        // 5 req/sec, burst 20
        return new RedisRateLimiter(5, 20, 1);
    }

    @Bean("aiRateLimiter")
    public RedisRateLimiter aiRateLimiter() {
        // Gemini API costs money — be strict per user
        // 1 req/2sec, burst 10
        return new RedisRateLimiter(1, 10, 2);  // requestedTokens=2 = "each call costs 2 tokens"
    }
}

