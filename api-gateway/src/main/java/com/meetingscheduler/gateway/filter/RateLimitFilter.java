package com.meetingscheduler.gateway.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class RateLimitFilter implements GlobalFilter, Ordered {

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 1. Whitelist /api/auth/** from rate limiting
        if (path.startsWith("/api/auth/")) {
            return chain.filter(exchange);
        }

        // 2. Extract X-User-Id set by JwtAuthFilter
        List<String> userIds = exchange.getRequest().getHeaders().get("X-User-Id");
        if (userIds == null || userIds.isEmpty()) {
            // If X-User-Id is missing on other routes, let the request flow
            // (JwtAuthFilter would have already intercepted it, but this adds a safety net)
            return chain.filter(exchange);
        }

        String userId = userIds.get(0);
        String key = "rate:" + userId;
        long now = System.currentTimeMillis();
        long windowStart = now - 3600000; // 1 hour ago

        Range<Double> range = Range.of(Range.Bound.unbounded(), Range.Bound.inclusive((double) windowStart));

        // 3. Sliding window Redis ZSET operations:
        //    a. ZREMRANGEBYSCORE key -inf windowStart
        //    b. ZADD key now now
        //    c. ZCARD key
        //    d. EXPIRE key 3600
        return redisTemplate.opsForZSet().removeRangeByScore(key, range)
                .then(redisTemplate.opsForZSet().add(key, String.valueOf(now), (double) now))
                .then(redisTemplate.opsForZSet().size(key))
                .flatMap(count -> redisTemplate.expire(key, Duration.ofSeconds(3600))
                        .then(Mono.just(count)))
                .flatMap(count -> {
                    if (count > 20) {
                        return handleTooManyRequests(exchange);
                    }
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> handleTooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Retry-After", "3600");
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again later.\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);

        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -5; // Runs after JwtAuthFilter (Order: -10)
    }
}
