package com.greatest.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Objects;

@Component
@Slf4j
public class UserKeyResolver implements KeyResolver {


    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {

        String userId = exchange.getAttribute("userId");
        if (userId != null && !userId.isBlank()) {
            log.debug("Rate-limit key = user:{}", userId);
            return Mono.just(userId);
        }

        InetSocketAddress address = exchange.getRequest().getRemoteAddress();
        String ip = (address != null && address.getAddress() != null) ? address.getAddress().getHostAddress() : "unknown-client";

        log.debug("Rate-limit key = ip:{}", ip);
        return Mono.just(ip);
    }
}
