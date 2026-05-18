package com.meetingscheduler.event.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BulkBusySlotsRequest(
    @NotEmpty(message = "User ID list cannot be empty")
    List<UUID> userIds,

    @NotNull(message = "From timestamp is required")
    Instant from,

    @NotNull(message = "To timestamp is required")
    Instant to
) {}
