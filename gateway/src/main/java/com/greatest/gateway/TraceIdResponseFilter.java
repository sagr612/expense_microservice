package com.greatest.gateway;

// 1. CHANGE THESE IMPORTS FROM BRAVE TO MICROMETER
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TraceIdResponseFilter implements GlobalFilter, Ordered {

    private final Tracer tracer;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        return chain.filter(exchange).doFinally(signal -> {
//            Span currentSpan = this.tracer.currentSpan();
//            if (currentSpan != null) {
//                String traceId = currentSpan.context().traceId();
//                ServerHttpResponse response = exchange.getResponse();
//                response.getHeaders().add("X-Trace-Id", traceId);
//            }
//        });

        return chain.filter(exchange).doOnSubscribe(sub -> {
            exchange.getResponse().beforeCommit(() -> {
                Span currentSpan = this.tracer.currentSpan();
                if (currentSpan != null) {
                    exchange.getResponse().getHeaders().add(
                            "X-Trace-Id", currentSpan.context().traceId());
                }
                return Mono.empty();
            });
        });

    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
