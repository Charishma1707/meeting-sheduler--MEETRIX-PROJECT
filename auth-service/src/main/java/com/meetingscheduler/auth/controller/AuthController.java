package com.meetingscheduler.auth.controller;

import com.meetingscheduler.auth.dto.request.LoginRequest;
import com.meetingscheduler.auth.dto.request.LogoutRequest;
import com.meetingscheduler.auth.dto.request.RefreshRequest;
import com.meetingscheduler.auth.dto.request.RegisterRequest;
import com.meetingscheduler.auth.dto.response.ApiResponse;
import com.meetingscheduler.auth.dto.response.AuthResponse;
import com.meetingscheduler.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ApiResponse<AuthResponse> register(@Validated @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login user")
    public ApiResponse<AuthResponse> login(@Validated @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ApiResponse<AuthResponse> refresh(@Validated @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "X-User-Id") String userId,
            @Validated @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.success(null);
    }
}
