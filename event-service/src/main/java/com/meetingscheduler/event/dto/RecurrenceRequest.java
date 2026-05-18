package com.meetingscheduler.event.dto;

import com.meetingscheduler.event.entity.RecurrenceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RecurrenceRequest(
    @NotNull(message = "Recurrence type is required")
    RecurrenceType type,

    @Min(value = 1, message = "Recurrence interval must be at least 1")
    int interval,

    @NotNull(message = "Recurrence until date is required")
    LocalDate until
) {}
