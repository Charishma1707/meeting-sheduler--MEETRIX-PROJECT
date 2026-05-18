package com.meetingscheduler.event.dto;

import java.time.Instant;
import java.util.UUID;

public record ConflictDetail(
    UUID userId,
    UUID conflictingEventId,
    String conflictingEventTitle,
    Instant conflictStart,
    Instant conflictEnd
) {}
