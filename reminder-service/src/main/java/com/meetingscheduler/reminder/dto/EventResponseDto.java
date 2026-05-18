package com.meetingscheduler.reminder.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventResponseDto(
    UUID id,
    String title,
    UUID organizerId,
    Instant startTime,
    List<InviteResponseDto> invites
) {}
