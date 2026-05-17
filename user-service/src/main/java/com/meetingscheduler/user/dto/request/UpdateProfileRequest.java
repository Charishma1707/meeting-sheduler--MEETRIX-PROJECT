package com.meetingscheduler.user.dto.request;

import com.meetingscheduler.user.entity.NotificationPreference;

public record UpdateProfileRequest(
        String name,
        String timezone,
        NotificationPreference notificationPreference
) {}
