package com.meetingscheduler.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Use a base64 encoded string as expected by JwtService init()
        String secretBase64 = Base64.getEncoder().encodeToString("mySecretKey12345678901234567890AB".getBytes());
        ReflectionTestUtils.setField(jwtService, "jwtSecret", secretBase64);
        ReflectionTestUtils.setField(jwtService, "jwtExpiryMs", 900000L);
        jwtService.init();
    }

    @Test
    void generateAccessToken_validInput_returnsNonNullToken() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "test@test.com");
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void generateAccessToken_tokenHasCorrectClaims() {
        UUID userId = UUID.randomUUID();
        String email = "test@test.com";
        String token = jwtService.generateAccessToken(userId, email);

        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractEmail(token)).isEqualTo(email);
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "test@test.com");
        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() throws InterruptedException {
        ReflectionTestUtils.setField(jwtService, "jwtExpiryMs", 1L);
        // We re-generate the token with 1ms expiry
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "test@test.com");
        
        Thread.sleep(10); // Wait for token to expire

        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_tamperedToken_returnsFalse() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), "test@test.com");
        String tamperedToken = token + "tampered";
        assertThat(jwtService.validateToken(tamperedToken)).isFalse();
    }

    @Test
    void validateToken_nullToken_returnsFalse() {
        assertThat(jwtService.validateToken(null)).isFalse();
    }

    @Test
    void validateToken_emptyToken_returnsFalse() {
        assertThat(jwtService.validateToken("")).isFalse();
    }

    @Test
    void extractUserId_validToken_returnsCorrectUUID() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateAccessToken(userId, "test@test.com");
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    void generateMultipleTokens_eachTokenIsUnique() {
        Set<String> tokens = new HashSet<>();
        tokens.add(jwtService.generateAccessToken(UUID.randomUUID(), "test1@test.com"));
        tokens.add(jwtService.generateAccessToken(UUID.randomUUID(), "test2@test.com"));
        tokens.add(jwtService.generateAccessToken(UUID.randomUUID(), "test3@test.com"));

        assertThat(tokens).hasSize(3);
    }
}
