package com.meetingscheduler.availability.dto;

public record FreeSlot(
    String startLocal,
    String endLocal,
    int durationMinutes
) {}
