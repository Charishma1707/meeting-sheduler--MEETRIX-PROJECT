package com.meetingscheduler.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record UpdateEventRequest(
    @NotBlank(message = "Title is required")
    String title,

    String description,
    String location,

    @NotBlank(message = "Start time is required")
    String startTimeLocal,

    @NotBlank(message = "End time is required")
    String endTimeLocal,

    @NotBlank(message = "Timezone is required")
    String timezone,

    @NotNull(message = "Invitee list cannot be null")
    List<UUID> inviteeIds,

    @Valid
    RecurrenceRequest recurrence
) {}
