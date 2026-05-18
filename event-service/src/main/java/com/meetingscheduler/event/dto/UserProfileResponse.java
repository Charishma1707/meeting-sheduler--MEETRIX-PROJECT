package com.meetingscheduler.event.dto;

import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String name,
    String email,
    String timezone,
    String notificationPreference
) {}
