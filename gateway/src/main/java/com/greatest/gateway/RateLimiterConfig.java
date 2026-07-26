package com.greatest.gateway;


import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {

    @Bean
    public RedisRateLimiter redisRateLimiter() {
//        System.out.println("CREATING REDIS RATE LIMITER");
        return new RedisRateLimiter(
                1, // replenishRate
                20, // burstCapacity
                1   // requestedTokens
        );
    }
}
