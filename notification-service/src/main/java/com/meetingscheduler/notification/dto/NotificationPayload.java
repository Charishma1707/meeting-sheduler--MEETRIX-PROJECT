package com.meetingscheduler.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationPayload(
    String type,
    String title,
    String message,
    UUID eventId,
    Instant timestamp
) {}
