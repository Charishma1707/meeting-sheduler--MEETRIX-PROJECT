package com.meetingscheduler.event.service;

import com.meetingscheduler.event.dto.TimeSlot;
import com.meetingscheduler.event.entity.RecurringRule;
import com.meetingscheduler.event.entity.RecurrenceType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;

@Component
public class RecurringRuleExpander {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<TimeSlot> expand(RecurringRule rule, Instant from, Instant to) {
        if (rule == null || rule.getEvent() == null) {
            return Collections.emptyList();
        }

        List<LocalDate> exceptions = parseExceptions(rule.getExceptions());
        Instant eventStart = rule.getEvent().getStartTime();
        Instant eventEnd = rule.getEvent().getEndTime();
        Duration eventDuration = Duration.between(eventStart, eventEnd);

        ZoneId zoneId = ZoneId.of(rule.getEvent().getTimezone());
        ZonedDateTime currentStart = eventStart.atZone(zoneId);

        LocalDate until = rule.getUntil();
        int interval = rule.getInterval();
        RecurrenceType type = rule.getType();

        List<TimeSlot> slots = new ArrayList<>();

        while (true) {
            LocalDate currentDate = currentStart.toLocalDate();

            if (currentDate.isAfter(until)) {
                break;
            }

            Instant occurrenceStart = currentStart.toInstant();
            if (occurrenceStart.isAfter(to)) {
                break;
            }

            Instant occurrenceEnd = occurrenceStart.plus(eventDuration);

            if (!exceptions.contains(currentDate)) {
                if (occurrenceEnd.isAfter(from) && occurrenceStart.isBefore(to)) {
                    slots.add(new TimeSlot(occurrenceStart, occurrenceEnd));
                }
            }

            switch (type) {
                case DAILY:
                    currentStart = currentStart.plusDays(interval);
                    break;
                case WEEKLY:
                    currentStart = currentStart.plusWeeks(interval);
                    break;
                case MONTHLY:
                    currentStart = currentStart.plusMonths(interval);
                    break;
            }
        }

        return slots;
    }

    private List<LocalDate> parseExceptions(String exceptionsJson) {
        if (exceptionsJson == null || exceptionsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> list = objectMapper.readValue(exceptionsJson, new TypeReference<List<String>>() {});
            List<LocalDate> dates = new ArrayList<>();
            for (String str : list) {
                dates.add(LocalDate.parse(str));
            }
            return dates;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
