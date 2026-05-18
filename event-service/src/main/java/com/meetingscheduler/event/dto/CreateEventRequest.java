package com.meetingscheduler.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateEventRequest(
    @NotBlank(message = "Title is required")
    String title,

    String description,
    String location,

    @NotBlank(message = "Start time is required")
    String startTimeLocal, // ISO-8601 string representation (e.g. "2026-05-18T10:00:00")

    @NotBlank(message = "End time is required")
    String endTimeLocal, // ISO-8601 string representation (e.g. "2026-05-18T11:00:00")

    @NotBlank(message = "Timezone is required")
    String timezone, // e.g. "Asia/Kolkata"

    @NotNull(message = "Invitee list cannot be null")
    List<UUID> inviteeIds,

    @Valid
    RecurrenceRequest recurrence
) {}
