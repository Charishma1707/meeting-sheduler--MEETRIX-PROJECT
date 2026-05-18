package com.meetingscheduler.event.dto;

import com.meetingscheduler.event.entity.RecurrenceType;
import java.time.LocalDate;
import java.util.List;

public record RecurrenceResponse(
    RecurrenceType type,
    int interval,
    LocalDate until,
    List<LocalDate> exceptions
) {}
