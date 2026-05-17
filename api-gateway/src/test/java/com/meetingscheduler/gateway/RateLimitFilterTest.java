package com.meetingscheduler.gateway;

import com.meetingscheduler.gateway.filter.RateLimitFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RateLimitFilterTest {

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveZSetOperations<String, String> zSetOperations;

    @Mock
    private GatewayFilterChain chain;

    @BeforeEach
    public void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    public void filter_underLimit_proceedsRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/me")
                        .header("X-User-Id", "user-123")
        );

        when(zSetOperations.removeRangeByScore(eq("rate:user-123"), any(Range.class))).thenReturn(Mono.just(0L));
        when(zSetOperations.add(eq("rate:user-123"), anyString(), anyDouble())).thenReturn(Mono.just(true));
        when(zSetOperations.size(eq("rate:user-123"))).thenReturn(Mono.just(15L)); // under 20 limit
        when(redisTemplate.expire(eq("rate:user-123"), any(Duration.class))).thenReturn(Mono.just(true));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = rateLimitFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    public void filter_atLimit_returns429WithRetryAfterHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/me")
                        .header("X-User-Id", "user-123")
        );

        when(zSetOperations.removeRangeByScore(eq("rate:user-123"), any(Range.class))).thenReturn(Mono.just(0L));
        when(zSetOperations.add(eq("rate:user-123"), anyString(), anyDouble())).thenReturn(Mono.just(true));
        when(zSetOperations.size(eq("rate:user-123"))).thenReturn(Mono.just(21L)); // over 20 limit
        when(redisTemplate.expire(eq("rate:user-123"), any(Duration.class))).thenReturn(Mono.just(true));

        Mono<Void> result = rateLimitFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verifyNoInteractions(chain);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("3600");
    }

    @Test
    public void filter_whitelistedPath_skipsRateLimit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/register")
        );

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = rateLimitFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(zSetOperations);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
