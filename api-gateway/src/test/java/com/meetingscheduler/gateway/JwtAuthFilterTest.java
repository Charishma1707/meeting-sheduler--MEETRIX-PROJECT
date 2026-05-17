package com.meetingscheduler.gateway;

import com.meetingscheduler.gateway.filter.JwtAuthFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtAuthFilterTest {

    private final String secret = "ZGV2ZWxvcG1lbnQtc2VjcmV0LWtleS1tdXN0LWJlLWF0LWxlYXN0LTMyLWJ5dGVzCg==";

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @Mock
    private GatewayFilterChain chain;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(jwtAuthFilter, "jwtSecret", secret);
        jwtAuthFilter.init();
    }

    @Test
    public void filter_whitelistedPath_proceedsWithoutTokenCheck() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
        );

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        Mono<Void> result = jwtAuthFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // No 401 set
    }

    @Test
    public void filter_validToken_addsUserIdAndEmailHeaders() {
        String token = Jwts.builder()
                .subject("user-123")
                .claim("email", "alice@test.com")
                .expiration(new Date(System.currentTimeMillis() + 600000)) // 10 minutes from now
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)))
                .compact();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/me")
                        .header("Authorization", "Bearer " + token)
        );

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(exchangeCaptor.capture())).thenReturn(Mono.empty());

        Mono<Void> result = jwtAuthFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        ServerWebExchange mutatedExchange = exchangeCaptor.getValue();
        assertThat(mutatedExchange).isNotNull();
        assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("user-123");
        assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-User-Email")).isEqualTo("alice@test.com");
        verify(chain, times(1)).filter(any(ServerWebExchange.class));
    }

    @Test
    public void filter_missingAuthHeader_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/me")
        );

        Mono<Void> result = jwtAuthFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verifyNoInteractions(chain);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void filter_invalidToken_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/me")
                        .header("Authorization", "Bearer invalidtokenbody")
        );

        Mono<Void> result = jwtAuthFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verifyNoInteractions(chain);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void filter_expiredToken_returns401() {
        String token = Jwts.builder()
                .subject("user-123")
                .claim("email", "alice@test.com")
                .expiration(new Date(System.currentTimeMillis() - 600000)) // expired 10 minutes ago
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)))
                .compact();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/me")
                        .header("Authorization", "Bearer " + token)
        );

        Mono<Void> result = jwtAuthFilter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verifyNoInteractions(chain);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
