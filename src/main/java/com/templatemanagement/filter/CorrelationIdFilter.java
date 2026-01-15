package com.templatemanagement.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WebFilter that ensures a correlation ID is present on all requests.
 * If not provided, generates a new one and adds it to both request and response headers.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated correlation ID: {}", correlationId);
        }

        final String finalCorrelationId = correlationId;

        // Add correlation ID to response headers
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);

        // Mutate request if correlation ID was generated
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(builder -> builder.header(CORRELATION_ID_HEADER, finalCorrelationId))
                .build();

        long startTime = System.currentTimeMillis();

        return chain.filter(mutatedExchange)
                .doOnSuccess(v -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Request completed: correlationId={}, method={}, path={}, status={}, duration={}ms",
                            finalCorrelationId,
                            request.getMethod(),
                            request.getPath().value(),
                            response.getStatusCode(),
                            duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("Request failed: correlationId={}, method={}, path={}, duration={}ms, error={}",
                            finalCorrelationId,
                            request.getMethod(),
                            request.getPath().value(),
                            duration,
                            error.getMessage());
                });
    }
}
