package com.meetingscheduler.availability.service;

import com.meetingscheduler.availability.algorithm.IntervalMerger;
import com.meetingscheduler.availability.dto.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IntervalMergerTest {

    private final IntervalMerger merger = new IntervalMerger();

    private TimeSlot slot(String start, String end) {
        return new TimeSlot(Instant.parse(start), Instant.parse(end));
    }

    @Test
    public void merge_emptyList_returnsEmpty() {
        assertTrue(merger.merge(null).isEmpty());
        assertTrue(merger.merge(Collections.emptyList()).isEmpty());
    }

    @Test
    public void merge_singleSlot_returnsSameSlot() {
        TimeSlot input = slot("2025-06-02T10:00:00Z", "2025-06-02T11:00:00Z");
        List<TimeSlot> result = merger.merge(List.of(input));
        assertEquals(1, result.size());
        assertEquals(input, result.get(0));
    }

    @Test
    public void merge_nonOverlappingSlots_returnsAllSeparate() {
        List<TimeSlot> inputs = List.of(
                slot("2025-06-02T09:00:00Z", "2025-06-02T10:00:00Z"),
                slot("2025-06-02T11:00:00Z", "2025-06-02T12:00:00Z"),
                slot("2025-06-02T13:00:00Z", "2025-06-02T14:00:00Z")
        );

        List<TimeSlot> result = merger.merge(inputs);

        assertEquals(3, result.size());
        assertEquals(Instant.parse("2025-06-02T09:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2025-06-02T11:00:00Z"), result.get(1).start());
        assertEquals(Instant.parse("2025-06-02T13:00:00Z"), result.get(2).start());
    }

    @Test
    public void merge_overlappingSlots_mergesIntoOne() {
        List<TimeSlot> inputs = List.of(
                slot("2025-06-02T09:00:00Z", "2025-06-02T10:30:00Z"),
                slot("2025-06-02T10:00:00Z", "2025-06-02T11:00:00Z")
        );

        List<TimeSlot> result = merger.merge(inputs);

        assertEquals(1, result.size());
        assertEquals(Instant.parse("2025-06-02T09:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2025-06-02T11:00:00Z"), result.get(0).end());
    }

    @Test
    public void merge_adjacentSlots_mergesIntoOne() {
        List<TimeSlot> inputs = List.of(
                slot("2025-06-02T09:00:00Z", "2025-06-02T10:00:00Z"),
                slot("2025-06-02T10:00:00Z", "2025-06-02T11:00:00Z")
        );

        List<TimeSlot> result = merger.merge(inputs);

        assertEquals(1, result.size());
        assertEquals(Instant.parse("2025-06-02T09:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2025-06-02T11:00:00Z"), result.get(0).end());
    }

    @Test
    public void merge_unsortedInput_sortsAndMergesCorrectly() {
        List<TimeSlot> inputs = List.of(
                slot("2025-06-02T13:00:00Z", "2025-06-02T14:00:00Z"),
                slot("2025-06-02T09:00:00Z", "2025-06-02T10:30:00Z"),
                slot("2025-06-02T10:00:00Z", "2025-06-02T11:00:00Z")
        );

        List<TimeSlot> result = merger.merge(inputs);

        assertEquals(2, result.size());
        assertEquals(Instant.parse("2025-06-02T09:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2025-06-02T11:00:00Z"), result.get(0).end());
        assertEquals(Instant.parse("2025-06-02T13:00:00Z"), result.get(1).start());
        assertEquals(Instant.parse("2025-06-02T14:00:00Z"), result.get(1).end());
    }

    @Test
    public void merge_containedSlot_absorbedByOuter() {
        List<TimeSlot> inputs = List.of(
                slot("2025-06-02T09:00:00Z", "2025-06-02T12:00:00Z"),
                slot("2025-06-02T10:00:00Z", "2025-06-02T11:00:00Z") // completely contained
        );

        List<TimeSlot> result = merger.merge(inputs);

        assertEquals(1, result.size());
        assertEquals(Instant.parse("2025-06-02T09:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2025-06-02T12:00:00Z"), result.get(0).end());
    }

    @Test
    public void merge_manyOverlapping_mergesIntoFewest() {
        List<TimeSlot> inputs = List.of(
                slot("2025-06-02T09:00:00Z", "2025-06-02T10:30:00Z"),
                slot("2025-06-02T10:00:00Z", "2025-06-02T11:30:00Z"),
                slot("2025-06-02T12:00:00Z", "2025-06-02T13:00:00Z"),
                slot("2025-06-02T12:30:00Z", "2025-06-02T14:00:00Z")
        );

        List<TimeSlot> result = merger.merge(inputs);

        assertEquals(2, result.size());
        assertEquals(Instant.parse("2025-06-02T09:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2025-06-02T11:30:00Z"), result.get(0).end());
        assertEquals(Instant.parse("2025-06-02T12:00:00Z"), result.get(1).start());
        assertEquals(Instant.parse("2025-06-02T14:00:00Z"), result.get(1).end());
    }

    @Test
    public void merge_doesNotMutateOriginalList() {
        List<TimeSlot> original = new ArrayList<>();
        original.add(slot("2025-06-02T12:00:00Z", "2025-06-02T13:00:00Z"));
        original.add(slot("2025-06-02T10:00:00Z", "2025-06-02T11:00:00Z"));

        List<TimeSlot> copy = new ArrayList<>(original);

        merger.merge(original);

        assertEquals(copy, original, "The original input list must NOT be modified");
    }
}
