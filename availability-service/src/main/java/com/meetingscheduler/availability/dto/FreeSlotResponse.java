package com.meetingscheduler.availability.dto;

import java.util.List;

public record FreeSlotResponse(
    List<FreeSlot> freeSlots,
    int totalFound
) {}
