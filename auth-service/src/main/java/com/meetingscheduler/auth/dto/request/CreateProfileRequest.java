package com.meetingscheduler.auth.dto.request;

import java.util.UUID;

public record CreateProfileRequest(
        UUID userId,
        String name,
        String email,
        String timezone
) {}
