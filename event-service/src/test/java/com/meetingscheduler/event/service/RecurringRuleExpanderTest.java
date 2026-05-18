package com.meetingscheduler.event.service;

import com.meetingscheduler.event.dto.TimeSlot;
import com.meetingscheduler.event.entity.Event;
import com.meetingscheduler.event.entity.RecurrenceType;
import com.meetingscheduler.event.entity.RecurringRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RecurringRuleExpanderTest {

    private final RecurringRuleExpander expander = new RecurringRuleExpander();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RecurringRule buildRule(RecurrenceType type, int interval, LocalDate until, List<LocalDate> exceptions) {
        Event event = Event.builder()
                .startTime(Instant.parse("2025-06-02T04:30:00Z")) // Monday June 2, 2025
                .endTime(Instant.parse("2025-06-02T05:00:00Z"))
                .timezone("Asia/Kolkata")
                .build();

        String exceptionsJson = "[]";
        try {
            if (exceptions != null && !exceptions.isEmpty()) {
                List<String> exceptionStrings = exceptions.stream().map(LocalDate::toString).toList();
                exceptionsJson = objectMapper.writeValueAsString(exceptionStrings);
            }
        } catch (Exception ignored) {}

        return RecurringRule.builder()
                .event(event)
                .type(type)
                .interval(interval)
                .until(until)
                .exceptions(exceptionsJson)
                .build();
    }

    @Test
    public void expand_weeklyRule_returnsCorrectOccurrences() {
        RecurringRule rule = buildRule(RecurrenceType.WEEKLY, 1, LocalDate.of(2025, 6, 30), Collections.emptyList());
        Instant from = Instant.parse("2025-06-01T00:00:00Z");
        Instant to = Instant.parse("2025-06-29T23:59:59Z");

        List<TimeSlot> slots = expander.expand(rule, from, to);

        assertEquals(4, slots.size(), "Should have exactly 4 weekly occurrences in June before the 30th");
        assertEquals(Instant.parse("2025-06-02T04:30:00Z"), slots.get(0).start());
        assertEquals(Instant.parse("2025-06-09T04:30:00Z"), slots.get(1).start());
        assertEquals(Instant.parse("2025-06-16T04:30:00Z"), slots.get(2).start());
        assertEquals(Instant.parse("2025-06-23T04:30:00Z"), slots.get(3).start());
    }

    @Test
    public void expand_weeklyRuleWithException_skipsExceptionDate() {
        RecurringRule rule = buildRule(RecurrenceType.WEEKLY, 1, LocalDate.of(2025, 6, 30), List.of(LocalDate.of(2025, 6, 9)));
        Instant from = Instant.parse("2025-06-01T00:00:00Z");
        Instant to = Instant.parse("2025-06-29T23:59:59Z");

        List<TimeSlot> slots = expander.expand(rule, from, to);

        assertEquals(3, slots.size(), "Should have 3 occurrences because June 9 is skipped");
        for (TimeSlot slot : slots) {
            assertNotEquals(Instant.parse("2025-06-09T04:30:00Z"), slot.start(), "No occurrence should start on June 9");
        }
        assertEquals(Instant.parse("2025-06-02T04:30:00Z"), slots.get(0).start());
        assertEquals(Instant.parse("2025-06-16T04:30:00Z"), slots.get(1).start());
        assertEquals(Instant.parse("2025-06-23T04:30:00Z"), slots.get(2).start());
    }

    @Test
    public void expand_dailyRule_returnsAllDaysInRange() {
        RecurringRule rule = buildRule(RecurrenceType.DAILY, 1, LocalDate.of(2025, 6, 5), Collections.emptyList());
        Instant from = Instant.parse("2025-06-01T00:00:00Z");
        Instant to = Instant.parse("2025-06-06T00:00:00Z");

        List<TimeSlot> slots = expander.expand(rule, from, to);

        assertEquals(4, slots.size(), "Should have exactly 4 daily occurrences: June 2, 3, 4, 5");
        assertEquals(Instant.parse("2025-06-02T04:30:00Z"), slots.get(0).start());
        assertEquals(Instant.parse("2025-06-03T04:30:00Z"), slots.get(1).start());
        assertEquals(Instant.parse("2025-06-04T04:30:00Z"), slots.get(2).start());
        assertEquals(Instant.parse("2025-06-05T04:30:00Z"), slots.get(3).start());
    }

    @Test
    public void expand_monthlyRule_returnsMonthlyOccurrences() {
        RecurringRule rule = buildRule(RecurrenceType.MONTHLY, 1, LocalDate.of(2025, 9, 30), Collections.emptyList());
        Instant from = Instant.parse("2025-06-01T00:00:00Z");
        Instant to = Instant.parse("2025-09-30T00:00:00Z");

        List<TimeSlot> slots = expander.expand(rule, from, to);

        assertEquals(4, slots.size(), "Should have 4 monthly occurrences: June 2, July 2, August 2, September 2");
        assertEquals(Instant.parse("2025-06-02T04:30:00Z"), slots.get(0).start());
        assertEquals(Instant.parse("2025-07-02T04:30:00Z"), slots.get(1).start());
        assertEquals(Instant.parse("2025-08-02T04:30:00Z"), slots.get(2).start());
        assertEquals(Instant.parse("2025-09-02T04:30:00Z"), slots.get(3).start());
    }

    @Test
    public void expand_rangeBeforeAllOccurrences_returnsEmpty() {
        RecurringRule rule = buildRule(RecurrenceType.WEEKLY, 1, LocalDate.of(2025, 6, 30), Collections.emptyList());
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");

        List<TimeSlot> slots = expander.expand(rule, from, to);

        assertTrue(slots.isEmpty(), "Should return empty list for date range before start date");
    }

    @Test
    public void expand_untilInPast_returnsEmpty() {
        RecurringRule rule = buildRule(RecurrenceType.WEEKLY, 1, LocalDate.of(2025, 5, 30), Collections.emptyList());
        Instant from = Instant.parse("2025-06-01T00:00:00Z");
        Instant to = Instant.parse("2025-06-30T00:00:00Z");

        List<TimeSlot> slots = expander.expand(rule, from, to);

        assertTrue(slots.isEmpty(), "Should return empty list if until is before event start date");
    }

    @Test
    public void expand_biweeklyRule_spacedExactly14DaysApart() {
        RecurringRule rule = buildRule(RecurrenceType.WEEKLY, 2, LocalDate.of(2025, 6, 30), Collections.emptyList());
        Instant from = Instant.parse("2025-06-01T00:00:00Z");
        Instant to = Instant.parse("2025-06-20T00:00:00Z");

        List<TimeSlot> slots = expander.expand(rule, from, to);

        assertEquals(2, slots.size(), "Should have exactly 2 occurrences spaced 2 weeks apart: June 2 and June 16");
        assertEquals(Instant.parse("2025-06-02T04:30:00Z"), slots.get(0).start());
        assertEquals(Instant.parse("2025-06-16T04:30:00Z"), slots.get(1).start());

        Duration diff = Duration.between(slots.get(0).start(), slots.get(1).start());
        assertEquals(14, diff.toDays(), "Occurrences should be spaced exactly 14 days apart");
    }
}
