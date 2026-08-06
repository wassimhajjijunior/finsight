package com.finsight.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final String START_TIME_ATTR = "startTime";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // Store start time in exchange attributes
        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());

        String correlationId = exchange.getRequest()
                .getHeaders()
                .getFirst(CORRELATION_ID_HEADER);

        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

        log.info("→ IN  | correlationId={} method={} path={} userId={}",
                correlationId, method, path, userId != null ? userId : "anonymous");

        // chain.filter() is the reactive equivalent of "proceed to next filter"
        // .then() executes AFTER the response comes back — this is the POST filter logic
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {

            Long startTime = exchange.getAttribute(START_TIME_ATTR);
            long duration = startTime != null
                    ? System.currentTimeMillis() - startTime
                    : -1;

            int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;

            log.info("← OUT | correlationId={} method={} path={} status={} duration={}ms",
                    correlationId, method, path, statusCode, duration);
        }));
    }

    @Override
    public int getOrder() {
        return -1; // runs last among our filters — after JWT injects X-User-Id
    }
}