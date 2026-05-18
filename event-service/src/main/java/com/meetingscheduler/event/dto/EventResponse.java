package com.meetingscheduler.event.dto;

import com.meetingscheduler.event.entity.EventStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventResponse(
    UUID id,
    String title,
    String description,
    String location,
    Instant startTime,
    Instant endTime,
    String startTimeLocal,
    String endTimeLocal,
    String timezone,
    UUID organizerId,
    EventStatus status,
    List<InviteResponse> invites,
    RecurrenceResponse recurrence
) {}
