package com.meetingscheduler.reminder.dto;

import java.util.UUID;

public record InviteResponseDto(
    UUID id,
    UUID inviteeId
) {}
