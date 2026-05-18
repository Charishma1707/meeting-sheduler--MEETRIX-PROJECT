package com.meetingscheduler.event.dto;

import com.meetingscheduler.event.entity.InviteStatus;
import java.time.Instant;
import java.util.UUID;

public record InviteResponse(
    UUID id,
    UUID inviteeId,
    InviteStatus status,
    Instant respondedAt
) {}
