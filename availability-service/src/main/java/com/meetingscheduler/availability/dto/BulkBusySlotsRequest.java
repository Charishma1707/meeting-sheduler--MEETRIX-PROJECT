package com.meetingscheduler.availability.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BulkBusySlotsRequest(
    List<UUID> userIds,
    Instant from,
    Instant to
) {}
