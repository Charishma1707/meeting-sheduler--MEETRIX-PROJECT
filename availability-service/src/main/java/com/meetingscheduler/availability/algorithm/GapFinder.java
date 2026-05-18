package com.meetingscheduler.availability.algorithm;

import com.meetingscheduler.availability.dto.TimeSlot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class GapFinder {

    /**
     * Finds gaps of free slots of at least durationMinutes within the specified time frame [from, to],
     * skipping busy time intervals.
     *
     * @param busyMerged      the list of merged, sorted busy intervals
     * @param from            the search start time boundary
     * @param to              the search end time boundary
     * @param durationMinutes the minimum slot duration in minutes required
     * @return a list of available free slots
     */
    public List<TimeSlot> findGaps(List<TimeSlot> busyMerged, Instant from, Instant to, int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Duration must be greater than zero");
        }

        if (from == null || to == null || !to.isAfter(from)) {
            return Collections.emptyList();
        }

        List<TimeSlot> gaps = new ArrayList<>();

        if (busyMerged == null || busyMerged.isEmpty()) {
            long totalMinutes = Duration.between(from, to).toMinutes();
            if (totalMinutes >= durationMinutes) {
                gaps.add(new TimeSlot(from, to));
            }
            return gaps;
        }

        // Filter and clamp busy slots to fit inside [from, to] window
        List<TimeSlot> clampedBusy = new ArrayList<>();
        for (TimeSlot slot : busyMerged) {
            // Overlap check
            if (slot.end().isAfter(from) && slot.start().isBefore(to)) {
                Instant start = slot.start().isBefore(from) ? from : slot.start();
                Instant end = slot.end().isAfter(to) ? to : slot.end();
                clampedBusy.add(new TimeSlot(start, end));
            }
        }

        if (clampedBusy.isEmpty()) {
            long totalMinutes = Duration.between(from, to).toMinutes();
            if (totalMinutes >= durationMinutes) {
                gaps.add(new TimeSlot(from, to));
            }
            return gaps;
        }

        // 1. Check gap from 'from' to first busy slot start
        TimeSlot first = clampedBusy.get(0);
        if (first.start().isAfter(from)) {
            long gap = Duration.between(from, first.start()).toMinutes();
            if (gap >= durationMinutes) {
                gaps.add(new TimeSlot(from, first.start()));
            }
        }

        // 2. Loop through consecutive pairs in clampedBusy
        for (int i = 1; i < clampedBusy.size(); i++) {
            TimeSlot previous = clampedBusy.get(i - 1);
            TimeSlot current = clampedBusy.get(i);

            long gap = Duration.between(previous.end(), current.start()).toMinutes();
            if (gap >= durationMinutes) {
                gaps.add(new TimeSlot(previous.end(), current.start()));
            }
        }

        // 3. Check gap from last busy slot end to 'to'
        TimeSlot last = clampedBusy.get(clampedBusy.size() - 1);
        if (last.end().isBefore(to)) {
            long gap = Duration.between(last.end(), to).toMinutes();
            if (gap >= durationMinutes) {
                gaps.add(new TimeSlot(last.end(), to));
            }
        }

        return gaps;
    }
}
