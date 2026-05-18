package com.meetingscheduler.event.dto;

import com.meetingscheduler.event.entity.InviteStatus;
import jakarta.validation.constraints.NotNull;

public record RsvpRequest(
    @NotNull(message = "Status is required")
    InviteStatus status // MUST be ACCEPTED or DECLINED
) {}
