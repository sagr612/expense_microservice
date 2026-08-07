package com.greatest.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
@Slf4j
public class UserKeyResolver implements KeyResolver {


    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {

        String userId = exchange.getRequest()
                .getHeaders()
                .getFirst("X-User-Id");

        if (userId != null) {
            log.info("Rate-limit key = user:{}", userId);
            return Mono.just(userId);
        }

        String ip =
                Objects.requireNonNull(
                                exchange.getRequest()
                                        .getRemoteAddress())
                        .getAddress()
                        .getHostAddress();
        log.info("Rate-limit key = ip:{}", ip);

        return Mono.just(ip);
    }
}
