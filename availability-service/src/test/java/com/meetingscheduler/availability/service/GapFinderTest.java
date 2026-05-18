package com.meetingscheduler.availability.service;

import com.meetingscheduler.availability.algorithm.GapFinder;
import com.meetingscheduler.availability.dto.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GapFinderTest {

    private final GapFinder finder = new GapFinder();
    private final Instant from = Instant.parse("2025-06-15T00:00:00Z");
    private final Instant to = Instant.parse("2025-06-15T23:59:59Z");

    @Test
    public void findGaps_noBusySlots_returnsEntireWindowAsOneGap() {
        List<TimeSlot> result = finder.findGaps(Collections.emptyList(), from, to, 30);
        assertEquals(1, result.size());
        assertEquals(from, result.get(0).start());
        assertEquals(to, result.get(0).end());
    }

    @Test
    public void findGaps_busyAllDay_returnsEmpty() {
        List<TimeSlot> busy = List.of(new TimeSlot(from, to));
        List<TimeSlot> result = finder.findGaps(busy, from, to, 30);
        assertTrue(result.isEmpty());
    }

    @Test
    public void findGaps_gapExactlyMatchesDuration_returnsIt() {
        // Busy covers: 00:00 - 10:00 and 10:30 - 23:59:59
        // Gap is: 10:00 - 10:30 (exactly 30 mins)
        List<TimeSlot> busy = List.of(
                new TimeSlot(from, Instant.parse("2025-06-15T10:00:00Z")),
                new TimeSlot(Instant.parse("2025-06-15T10:30:00Z"), to)
        );

        List<TimeSlot> result = finder.findGaps(busy, from, to, 30);

        assertEquals(1, result.size());
        assertEquals(Instant.parse("2025-06-15T10:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2025-06-15T10:30:00Z"), result.get(0).end());
    }

    @Test
    public void findGaps_gapSmallerThanDuration_notReturned() {
        // Busy covers: 00:00 - 10:00 and 10:20 - 23:59:59
        // Gap is: 10:00 - 10:20 (20 mins)
        List<TimeSlot> busy = List.of(
                new TimeSlot(from, Instant.parse("2025-06-15T10:00:00Z")),
                new TimeSlot(Instant.parse("2025-06-15T10:20:00Z"), to)
        );

        List<TimeSlot> result = finder.findGaps(busy, from, to, 30);

        assertTrue(result.isEmpty());
    }

    @Test
    public void findGaps_multipleGaps_returnsAllValid() {
        // 3 busy slots leaving 4 gaps
        List<TimeSlot> busy = List.of(
                new TimeSlot(Instant.parse("2025-06-15T10:00:00Z"), Instant.parse("2025-06-15T11:00:00Z")),
                new TimeSlot(Instant.parse("2025-06-15T13:00:00Z"), Instant.parse("2025-06-15T14:00:00Z")),
                new TimeSlot(Instant.parse("2025-06-15T17:00:00Z"), Instant.parse("2025-06-15T18:00:00Z"))
        );

        List<TimeSlot> result = finder.findGaps(busy, from, to, 60);

        assertEquals(4, result.size());
        // Gap 1: start to busy[0]
        assertEquals(from, result.get(0).start());
        assertEquals(Instant.parse("2025-06-15T10:00:00Z"), result.get(0).end());
        // Gap 2: busy[0] to busy[1]
        assertEquals(Instant.parse("2025-06-15T11:00:00Z"), result.get(1).start());
        assertEquals(Instant.parse("2025-06-15T13:00:00Z"), result.get(1).end());
        // Gap 3: busy[1] to busy[2]
        assertEquals(Instant.parse("2025-06-15T14:00:00Z"), result.get(2).start());
        assertEquals(Instant.parse("2025-06-15T17:00:00Z"), result.get(2).end());
        // Gap 4: busy[2] to end
        assertEquals(Instant.parse("2025-06-15T18:00:00Z"), result.get(3).start());
        assertEquals(to, result.get(3).end());
    }

    @Test
    public void findGaps_gapAtStartOfWindow_included() {
        // Busy starts at 01:00, leaving 00:00 - 01:00 (60 mins) open
        List<TimeSlot> busy = List.of(
                new TimeSlot(Instant.parse("2025-06-15T01:00:00Z"), to)
        );

        List<TimeSlot> result = finder.findGaps(busy, from, to, 30);

        assertEquals(1, result.size());
        assertEquals(from, result.get(0).start());
        assertEquals(Instant.parse("2025-06-15T01:00:00Z"), result.get(0).end());
    }

    @Test
    public void findGaps_gapAtEndOfWindow_included() {
        // Busy ends at 23:00, leaving 23:00 - 23:59:59 open
        List<TimeSlot> busy = List.of(
                new TimeSlot(from, Instant.parse("2025-06-15T23:00:00Z"))
        );

        List<TimeSlot> result = finder.findGaps(busy, from, to, 30);

        assertEquals(1, result.size());
        assertEquals(Instant.parse("2025-06-15T23:00:00Z"), result.get(0).start());
        assertEquals(to, result.get(0).end());
    }

    @Test
    public void findGaps_zeroDuration_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> finder.findGaps(Collections.emptyList(), from, to, 0));
        assertThrows(IllegalArgumentException.class, () -> finder.findGaps(Collections.emptyList(), from, to, -10));
    }
}
