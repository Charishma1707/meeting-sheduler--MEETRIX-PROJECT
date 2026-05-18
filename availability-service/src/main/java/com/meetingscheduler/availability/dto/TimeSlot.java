package com.meetingscheduler.availability.dto;

import java.time.Instant;

public record TimeSlot(
    Instant start,
    Instant end
) {}
