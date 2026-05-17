package com.meetingscheduler.user.dto.response;

import com.meetingscheduler.user.entity.NotificationPreference;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String name,
        String email,
        String timezone,
        NotificationPreference notificationPreference
) {}
