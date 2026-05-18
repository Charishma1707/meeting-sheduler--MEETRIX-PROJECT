package com.meetingscheduler.event.dto;

import java.util.List;
import java.util.UUID;

public record BatchUserRequest(
    List<UUID> userIds
) {}
