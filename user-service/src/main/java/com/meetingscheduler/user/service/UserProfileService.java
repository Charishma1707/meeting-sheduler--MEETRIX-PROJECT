package com.meetingscheduler.user.service;

import com.meetingscheduler.user.dto.request.CreateProfileRequest;
import com.meetingscheduler.user.dto.request.UpdateProfileRequest;
import com.meetingscheduler.user.dto.response.UserProfileResponse;
import com.meetingscheduler.user.entity.UserProfile;
import com.meetingscheduler.user.exception.ConflictException;
import com.meetingscheduler.user.exception.ResourceNotFoundException;
import com.meetingscheduler.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    @Transactional
    public UserProfileResponse createProfile(CreateProfileRequest request) {
        log.info("Creating profile for user ID: {}", request.userId());

        if (userProfileRepository.existsById(request.userId())) {
            throw new ConflictException("UserProfile already exists for user ID: " + request.userId());
        }

        validateTimezone(request.timezone());

        UserProfile profile = UserProfile.builder()
                .id(request.userId())
                .name(request.name())
                .email(request.email())
                .timezone(request.timezone())
                .build();

        UserProfile saved = userProfileRepository.save(profile);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfileById(UUID id) {
        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile not found for ID: " + id));
        return toResponse(profile);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID id, UpdateProfileRequest request) {
        log.info("Updating profile for user ID: {}", id);

        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserProfile not found for ID: " + id));

        if (request.name() != null) {
            profile.setName(request.name());
        }

        if (request.timezone() != null) {
            validateTimezone(request.timezone());
            profile.setTimezone(request.timezone());
        }

        if (request.notificationPreference() != null) {
            profile.setNotificationPreference(request.notificationPreference());
        }

        UserProfile saved = userProfileRepository.save(profile);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> searchProfiles(String query) {
        log.info("Searching profiles with query: {}", query);
        List<UserProfile> profiles;
        if (query == null || query.isBlank()) {
            profiles = userProfileRepository.findAll();
        } else {
            profiles = userProfileRepository.findByNameContainingIgnoreCase(query);
        }
        return profiles.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> getProfilesBatch(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            log.info("Fetching profiles batch: empty or null input list");
            return List.of();
        }
        log.info("Fetching profiles batch for {} IDs", ids.size());
        return userProfileRepository.findAllByIdIn(ids).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone);
        }
    }

    private UserProfileResponse toResponse(UserProfile profile) {
        return new UserProfileResponse(
                profile.getId(),
                profile.getName(),
                profile.getEmail(),
                profile.getTimezone(),
                profile.getNotificationPreference()
        );
    }
}
