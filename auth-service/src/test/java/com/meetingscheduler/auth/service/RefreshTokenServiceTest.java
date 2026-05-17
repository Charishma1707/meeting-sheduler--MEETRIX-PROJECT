package com.meetingscheduler.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpiryDays", 7L);
    }

    @Test
    void createRefreshToken_validUserId_storesInRedisWithCorrectKeyAndTTL() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        
        UUID userId = UUID.randomUUID();
        String token = refreshTokenService.createRefreshToken(userId);

        assertThat(token).isNotNull();
        String expectedKey = "refresh:" + token;
        verify(valueOps).set(expectedKey, userId.toString(), Duration.ofDays(7));
    }

    @Test
    void createRefreshToken_returnsUniqueTokenEachCall() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        Set<String> tokens = new HashSet<>();
        tokens.add(refreshTokenService.createRefreshToken(UUID.randomUUID()));
        tokens.add(refreshTokenService.createRefreshToken(UUID.randomUUID()));
        tokens.add(refreshTokenService.createRefreshToken(UUID.randomUUID()));

        assertThat(tokens).hasSize(3);
    }

    @Test
    void getUserIdFromRefreshToken_existingToken_returnsUserId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        String token = UUID.randomUUID().toString();
        UUID expectedUserId = UUID.randomUUID();
        
        when(valueOps.get("refresh:" + token)).thenReturn(expectedUserId.toString());
        
        UUID actualUserId = refreshTokenService.getUserIdFromToken(token);
        
        assertThat(actualUserId).isEqualTo(expectedUserId);
    }

    @Test
    void getUserIdFromRefreshToken_nonExistentToken_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        String token = UUID.randomUUID().toString();
        
        when(valueOps.get("refresh:" + token)).thenReturn(null);
        
        UUID actualUserId = refreshTokenService.getUserIdFromToken(token);
        
        assertThat(actualUserId).isNull();
    }

    @Test
    void deleteRefreshToken_deletesCorrectKeyFromRedis() {
        String token = UUID.randomUUID().toString();
        refreshTokenService.deleteRefreshToken(token);
        verify(redisTemplate).delete("refresh:" + token);
    }

    @Test
    void deleteRefreshToken_nonExistentKey_doesNotThrow() {
        String token = UUID.randomUUID().toString();
        refreshTokenService.deleteRefreshToken(token);
        verify(redisTemplate).delete("refresh:" + token);
    }
}
