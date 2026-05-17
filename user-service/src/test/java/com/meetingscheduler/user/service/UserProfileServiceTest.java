package com.meetingscheduler.user.service;

import com.meetingscheduler.user.dto.request.CreateProfileRequest;
import com.meetingscheduler.user.dto.request.UpdateProfileRequest;
import com.meetingscheduler.user.dto.response.UserProfileResponse;
import com.meetingscheduler.user.entity.NotificationPreference;
import com.meetingscheduler.user.entity.UserProfile;
import com.meetingscheduler.user.exception.ConflictException;
import com.meetingscheduler.user.exception.ResourceNotFoundException;
import com.meetingscheduler.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    private UUID userId;
    private UserProfile mockProfile;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockProfile = UserProfile.builder()
                .id(userId)
                .name("Alice")
                .email("alice@test.com")
                .timezone("Asia/Kolkata")
                .notificationPreference(NotificationPreference.BOTH)
                .build();
    }

    @Test
    void createProfile_newUser_savesAndReturnsProfile() {
        CreateProfileRequest request = new CreateProfileRequest(userId, "Alice", "alice@test.com", "Asia/Kolkata");

        when(userProfileRepository.existsById(userId)).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(mockProfile);

        UserProfileResponse response = userProfileService.createProfile(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.name()).isEqualTo("Alice");
        assertThat(response.email()).isEqualTo("alice@test.com");
        assertThat(response.timezone()).isEqualTo("Asia/Kolkata");
        assertThat(response.notificationPreference()).isEqualTo(NotificationPreference.BOTH);

        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void createProfile_duplicateUserId_throwsConflictException() {
        CreateProfileRequest request = new CreateProfileRequest(userId, "Alice", "alice@test.com", "Asia/Kolkata");

        when(userProfileRepository.existsById(userId)).thenReturn(true);

        assertThatThrownBy(() -> userProfileService.createProfile(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("UserProfile already exists for user ID: " + userId);

        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    void getProfile_existingUser_returnsCorrectProfile() {
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(mockProfile));

        UserProfileResponse response = userProfileService.getProfileById(userId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.name()).isEqualTo("Alice");
        assertThat(response.email()).isEqualTo("alice@test.com");
        assertThat(response.timezone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void getProfile_nonExistentUser_throwsResourceNotFoundException() {
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getProfileById(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(userId.toString());
    }

    @Test
    void updateProfile_updatesOnlyNonNullFields() {
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(mockProfile));

        UpdateProfileRequest updateRequest = new UpdateProfileRequest("Alice Updated", null, null);

        UserProfile updatedProfile = UserProfile.builder()
                .id(userId)
                .name("Alice Updated")
                .email("alice@test.com")
                .timezone("Asia/Kolkata")
                .notificationPreference(NotificationPreference.BOTH)
                .build();

        when(userProfileRepository.save(any(UserProfile.class))).thenReturn(updatedProfile);

        userProfileService.updateProfile(userId, updateRequest);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());

        UserProfile captured = captor.getValue();
        assertThat(captured.getName()).isEqualTo("Alice Updated");
        assertThat(captured.getTimezone()).isEqualTo("Asia/Kolkata");
        assertThat(captured.getNotificationPreference()).isEqualTo(NotificationPreference.BOTH);
    }

    @Test
    void searchUsers_matchingName_returnsList() {
        when(userProfileRepository.findByNameContainingIgnoreCase("Ali")).thenReturn(List.of(mockProfile));

        List<UserProfileResponse> responses = userProfileService.searchProfiles("Ali");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Alice");
    }

    @Test
    void searchUsers_noMatch_returnsEmptyList() {
        when(userProfileRepository.findByNameContainingIgnoreCase("Unknown")).thenReturn(List.of());

        List<UserProfileResponse> responses = userProfileService.searchProfiles("Unknown");

        assertThat(responses).isEmpty();
    }

    @Test
    void batchGetUsers_validIds_returnsAllProfiles() {
        when(userProfileRepository.findAllByIdIn(List.of(userId))).thenReturn(List.of(mockProfile));

        List<UserProfileResponse> responses = userProfileService.getProfilesBatch(List.of(userId));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(userId);
    }

    @Test
    void batchGetUsers_emptyList_returnsEmpty_withoutCallingRepository() {
        List<UserProfileResponse> responses = userProfileService.getProfilesBatch(List.of());

        assertThat(responses).isEmpty();
        verify(userProfileRepository, never()).findAllByIdIn(any());
    }
}
