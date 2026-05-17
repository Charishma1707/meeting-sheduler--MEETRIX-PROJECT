package com.meetingscheduler.auth.service;

import com.meetingscheduler.auth.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refreshExpiryDays}")
    private long refreshExpiryDays;

    public String createRefreshToken(UUID userId) {
        String refreshToken = UUID.randomUUID().toString();
        String key = Constants.REFRESH_TOKEN_PREFIX + refreshToken;
        redisTemplate.opsForValue().set(key, userId.toString(), Duration.ofDays(refreshExpiryDays));
        return refreshToken;
    }

    public UUID getUserIdFromToken(String refreshToken) {
        String key = Constants.REFRESH_TOKEN_PREFIX + refreshToken;
        String userIdStr = redisTemplate.opsForValue().get(key);
        if (userIdStr != null) {
            return UUID.fromString(userIdStr);
        }
        return null;
    }

    public void deleteRefreshToken(String refreshToken) {
        String key = Constants.REFRESH_TOKEN_PREFIX + refreshToken;
        redisTemplate.delete(key);
    }
}
