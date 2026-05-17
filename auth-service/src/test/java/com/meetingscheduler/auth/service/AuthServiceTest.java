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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserCredentialRepository userCredentialRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_newEmail_savesCredentialAndReturnsTokens() {
        RegisterRequest request = new RegisterRequest("Test User", "test@test.com", "password123", "UTC");
        
        when(userCredentialRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashedPassword");
        
        UUID userId = UUID.randomUUID();
        UserCredential credential = UserCredential.builder()
                .id(userId)
                .email(request.email())
                .passwordHash("hashedPassword")
                .build();
                
        when(userCredentialRepository.save(any(UserCredential.class))).thenReturn(credential);
        when(jwtService.generateAccessToken(userId, request.email())).thenReturn("accessToken");
        when(refreshTokenService.createRefreshToken(userId)).thenReturn("refreshToken");

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("accessToken");
        assertThat(response.refreshToken()).isEqualTo("refreshToken");
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo(request.email());
        
        verify(userCredentialRepository).save(any(UserCredential.class));
        verify(userServiceClient).createProfile(any(CreateProfileRequest.class));
    }

    @Test
    void register_existingEmail_throwsConflictException() {
        RegisterRequest request = new RegisterRequest("Test User", "test@test.com", "password123", "UTC");
        when(userCredentialRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already exists");
                
        verify(userCredentialRepository, never()).save(any(UserCredential.class));
        verify(userServiceClient, never()).createProfile(any());
    }

    @Test
    void register_userServiceClientFails_registrationStillSucceeds() {
        RegisterRequest request = new RegisterRequest("Test User", "test@test.com", "password123", "UTC");
        
        when(userCredentialRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashedPassword");
        
        UUID userId = UUID.randomUUID();
        UserCredential credential = UserCredential.builder()
                .id(userId)
                .email(request.email())
                .passwordHash("hashedPassword")
                .build();
                
        when(userCredentialRepository.save(any(UserCredential.class))).thenReturn(credential);
        
        // Simulating the user-service client failing, for example throwing a RuntimeException
        doThrow(RuntimeException.class).when(userServiceClient).createProfile(any(CreateProfileRequest.class));
        
        when(jwtService.generateAccessToken(userId, request.email())).thenReturn("accessToken");
        when(refreshTokenService.createRefreshToken(userId)).thenReturn("refreshToken");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("accessToken");
        verify(userServiceClient).createProfile(any(CreateProfileRequest.class));
    }

    @Test
    void login_correctCredentials_returnsTokens() {
        LoginRequest request = new LoginRequest("test@test.com", "password123");
        
        UUID userId = UUID.randomUUID();
        UserCredential credential = UserCredential.builder()
                .id(userId)
                .email(request.email())
                .passwordHash("hashedPassword")
                .build();
                
        when(userCredentialRepository.findByEmail(request.email())).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches(request.password(), credential.getPasswordHash())).thenReturn(true);
        
        when(jwtService.generateAccessToken(userId, request.email())).thenReturn("accessToken");
        when(refreshTokenService.createRefreshToken(userId)).thenReturn("refreshToken");
        
        AuthResponse response = authService.login(request);
        
        assertThat(response.accessToken()).isEqualTo("accessToken");
        assertThat(response.refreshToken()).isEqualTo("refreshToken");
    }

    @Test
    void login_unknownEmail_throwsUnauthorizedException() {
        LoginRequest request = new LoginRequest("test@test.com", "password123");
        when(userCredentialRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_wrongPassword_throwsUnauthorizedException() {
        LoginRequest request = new LoginRequest("test@test.com", "wrongPassword");
        
        UUID userId = UUID.randomUUID();
        UserCredential credential = UserCredential.builder()
                .id(userId)
                .email(request.email())
                .passwordHash("hashedPassword")
                .build();
                
        when(userCredentialRepository.findByEmail(request.email())).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches(request.password(), credential.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
                
        verify(jwtService, never()).generateAccessToken(any(), anyString());
    }

    @Test
    void refresh_validToken_returnsNewAccessToken() {
        String refreshToken = "valid-refresh-token";
        UUID userId = UUID.randomUUID();
        
        when(refreshTokenService.getUserIdFromToken(refreshToken)).thenReturn(userId);
        
        UserCredential credential = UserCredential.builder()
                .id(userId)
                .email("test@test.com")
                .build();
        when(userCredentialRepository.findById(userId)).thenReturn(Optional.of(credential));
        
        when(jwtService.generateAccessToken(userId, credential.getEmail())).thenReturn("newAccessToken");
        
        AuthResponse response = authService.refresh(refreshToken);
        
        assertThat(response.accessToken()).isEqualTo("newAccessToken");
        assertThat(response.refreshToken()).isEqualTo(refreshToken);
        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    void refresh_invalidToken_throwsUnauthorizedException() {
        String refreshToken = "invalid-refresh-token";
        when(refreshTokenService.getUserIdFromToken(refreshToken)).thenReturn(null);

        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid or expired refresh token");
    }

    @Test
    void logout_deletesRefreshTokenFromRedis() {
        String refreshToken = "token-to-delete";
        authService.logout(refreshToken);
        verify(refreshTokenService).deleteRefreshToken(refreshToken);
    }
}
