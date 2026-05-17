package com.meetingscheduler.user.controller;

import com.meetingscheduler.user.dto.request.BatchUserRequest;
import com.meetingscheduler.user.dto.request.CreateProfileRequest;
import com.meetingscheduler.user.dto.request.UpdateProfileRequest;
import com.meetingscheduler.user.dto.response.UserProfileResponse;
import com.meetingscheduler.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    @PostMapping("/internal/create")
    public ResponseEntity<UserProfileResponse> createProfile(@Valid @RequestBody CreateProfileRequest request) {
        log.info("Received request to create profile internally for user ID: {}", request.userId());
        UserProfileResponse response = userProfileService.createProfile(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@RequestHeader("X-User-Id") UUID userId) {
        log.info("Received request to fetch profile for user ID: {}", userId);
        UserProfileResponse response = userProfileService.getProfileById(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody UpdateProfileRequest request) {
        log.info("Received request to update profile for user ID: {}", userId);
        UserProfileResponse response = userProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getProfileById(@PathVariable("id") UUID id) {
        log.info("Received request to fetch profile for ID: {}", id);
        UserProfileResponse response = userProfileService.getProfileById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserProfileResponse>> searchProfiles(@RequestParam(value = "q", required = false) String query) {
        log.info("Received request to search profiles with query: {}", query);
        List<UserProfileResponse> responses = userProfileService.searchProfiles(query);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/internal/batch")
    public ResponseEntity<List<UserProfileResponse>> getProfilesBatch(@Valid @RequestBody BatchUserRequest request) {
        log.info("Received request to fetch profiles batch for {} IDs", request.userIds().size());
        List<UserProfileResponse> responses = userProfileService.getProfilesBatch(request.userIds());
        return ResponseEntity.ok(responses);
    }
}
