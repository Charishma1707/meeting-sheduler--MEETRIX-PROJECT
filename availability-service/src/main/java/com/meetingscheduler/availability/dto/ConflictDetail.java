package com.meetingscheduler.availability.dto;

import java.time.Instant;
import java.util.UUID;

public record ConflictDetail(
    UUID userId,
    UUID conflictingEventId,
    String conflictingEventTitle,
    Instant conflictStart,
    Instant conflictEnd
) {}
