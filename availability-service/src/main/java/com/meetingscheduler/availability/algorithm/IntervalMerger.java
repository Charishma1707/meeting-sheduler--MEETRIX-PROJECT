package com.meetingscheduler.availability.algorithm;

import com.meetingscheduler.availability.dto.TimeSlot;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class IntervalMerger {

    /**
     * Merges overlapping or touching time slots into a minimal set of disjoint intervals.
     * 
     * Time Complexity: O(n log n) due to the sorting step where n is the number of slots.
     * Space Complexity: O(n) to store the cloned list and result intervals.
     * 
     * This algorithm is non-mutating and does NOT modify the input list.
     *
     * @param slots the list of busy time slots to merge
     * @return a sorted, non-overlapping, merged list of TimeSlot objects
     */
    public List<TimeSlot> merge(List<TimeSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return Collections.emptyList();
        }

        // Copy the list to avoid mutating the original input list (MANDATORY REQUIREMENT)
        List<TimeSlot> sortedSlots = new ArrayList<>(slots);

        // Sort by start time ascending
        sortedSlots.sort(Comparator.comparing(TimeSlot::start));

        List<TimeSlot> merged = new ArrayList<>();
        merged.add(sortedSlots.get(0));

        for (int i = 1; i < sortedSlots.size(); i++) {
            TimeSlot current = sortedSlots.get(i);
            TimeSlot last = merged.get(merged.size() - 1);

            // Overlap or touch condition: current start is less than or equal to last end
            if (!current.start().isAfter(last.end())) {
                // Merge by updating the end time to the maximum of both ends
                if (current.end().isAfter(last.end())) {
                    merged.set(merged.size() - 1, new TimeSlot(last.start(), current.end()));
                }
            } else {
                merged.add(current);
            }
        }

        return merged;
    }
}
