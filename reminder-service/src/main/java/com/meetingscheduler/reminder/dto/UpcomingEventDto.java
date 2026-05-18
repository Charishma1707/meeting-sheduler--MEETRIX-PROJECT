package com.meetingscheduler.reminder.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpcomingEventDto(
    UUID eventId,
    String title,
    UUID organizerId,
    List<UUID> inviteeIds,
    Instant startTime
) {}
