package com.meetingscheduler.availability.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record FreeSlotRequest(
    @NotEmpty(message = "User IDs list cannot be empty")
    List<UUID> userIds,

    @Min(value = 1, message = "Duration must be at least 1 minute")
    int durationMinutes,

    @NotNull(message = "From local timestamp is required")
    String fromLocal,

    @NotNull(message = "To local timestamp is required")
    String toLocal,

    @NotEmpty(message = "Timezone is required")
    String timezone
) {}
