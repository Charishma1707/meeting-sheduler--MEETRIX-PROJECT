package com.meetingscheduler.notification.dto;

import java.util.List;
import java.util.UUID;

public record BatchUserRequest(
    List<UUID> userIds
) {}
