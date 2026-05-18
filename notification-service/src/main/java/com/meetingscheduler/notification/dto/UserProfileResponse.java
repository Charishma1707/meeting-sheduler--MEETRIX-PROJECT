package com.meetingscheduler.notification.dto;

import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String name,
    String email,
    String timezone,
    NotificationPreference notificationPreference
) {}
