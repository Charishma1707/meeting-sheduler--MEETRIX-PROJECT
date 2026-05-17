package com.meetingscheduler.user.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record BatchUserRequest(
        @NotEmpty(message = "User IDs list cannot be empty")
        List<UUID> userIds
) {}
