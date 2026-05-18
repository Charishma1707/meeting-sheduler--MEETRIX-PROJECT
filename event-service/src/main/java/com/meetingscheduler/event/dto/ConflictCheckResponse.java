package com.meetingscheduler.event.dto;

import java.util.List;

public record ConflictCheckResponse(
    boolean hasConflict,
    List<ConflictDetail> conflicts
) {}
