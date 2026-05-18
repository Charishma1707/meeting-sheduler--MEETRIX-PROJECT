package com.meetingscheduler.availability.dto;

import java.util.List;

public record ConflictCheckResponse(
    boolean hasConflict,
    List<ConflictDetail> conflicts
) {}
