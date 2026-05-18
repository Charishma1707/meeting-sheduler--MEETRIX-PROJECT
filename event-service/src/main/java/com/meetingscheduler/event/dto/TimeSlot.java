package com.meetingscheduler.event.dto;

import java.time.Instant;

public record TimeSlot(
    Instant start,
    Instant end
) {}
