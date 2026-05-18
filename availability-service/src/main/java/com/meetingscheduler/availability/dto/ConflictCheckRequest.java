package com.meetingscheduler.availability.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConflictCheckRequest(
    @NotEmpty(message = "User IDs list cannot be empty")
    List<UUID> userIds,

    @NotNull(message = "Start time is required")
    Instant startTime,

    @NotNull(message = "End time is required")
    Instant endTime
) {}
