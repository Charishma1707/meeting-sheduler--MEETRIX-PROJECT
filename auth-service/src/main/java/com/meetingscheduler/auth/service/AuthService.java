package com.meetingscheduler.auth.service;

import com.meetingscheduler.auth.client.UserServiceClient;
import com.meetingscheduler.auth.dto.request.CreateProfileRequest;
import com.meetingscheduler.auth.dto.request.LoginRequest;
import com.meetingscheduler.auth.dto.request.RegisterRequest;
import com.meetingscheduler.auth.dto.response.AuthResponse;
import com.meetingscheduler.auth.entity.UserCredential;
import com.meetingscheduler.auth.exception.ConflictException;
import com.meetingscheduler.auth.exception.UnauthorizedException;
import com.meetingscheduler.auth.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserCredentialRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserServiceClient userServiceClient;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new ConflictException("Email already exists");
        }

        UserCredential credential = UserCredential.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        credential = repository.save(credential);

        try {
            userServiceClient.createProfile(new CreateProfileRequest(
                    credential.getId(),
                    request.name(),
                    request.email(),
                    request.timezone()
            ));
        } catch (Exception e) {
            log.warn("Failed to call user-service for user {}. Registration succeeds but profile needs manual sync.", credential.getId(), e);
        }

        String accessToken = jwtService.generateAccessToken(credential.getId(), credential.getEmail());
        String refreshToken = refreshTokenService.createRefreshToken(credential.getId());

        return new AuthResponse(accessToken, refreshToken, credential.getId(), credential.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserCredential credential = repository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String accessToken = jwtService.generateAccessToken(credential.getId(), credential.getEmail());
        String refreshToken = refreshTokenService.createRefreshToken(credential.getId());

        return new AuthResponse(accessToken, refreshToken, credential.getId(), credential.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        UUID userId = refreshTokenService.getUserIdFromToken(refreshToken);
        if (userId == null) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        UserCredential credential = repository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        String accessToken = jwtService.generateAccessToken(credential.getId(), credential.getEmail());

        return new AuthResponse(accessToken, refreshToken, credential.getId(), credential.getEmail());
    }

    public void logout(String refreshToken) {
        refreshTokenService.deleteRefreshToken(refreshToken);
    }
}
