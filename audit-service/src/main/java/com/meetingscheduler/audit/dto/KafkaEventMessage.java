package com.meetingscheduler.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record KafkaEventMessage(
    UUID eventId,
    String eventType,
    UUID actorUserId,
    Instant timestamp,
    String payload
) {}
