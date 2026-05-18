package com.meetingscheduler.availability.service;

import com.meetingscheduler.availability.algorithm.GapFinder;
import com.meetingscheduler.availability.algorithm.IntervalMerger;
import com.meetingscheduler.availability.client.EventServiceClient;
import com.meetingscheduler.availability.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityService {

    private final EventServiceClient eventServiceClient;
    private final IntervalMerger intervalMerger;
    private final GapFinder gapFinder;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public FreeSlotResponse getFreeSlots(FreeSlotRequest request) {
        log.info("Calculating free slots for users size: {} in timezone: {}", request.userIds().size(), request.timezone());

        ZoneId zoneId = ZoneId.of(request.timezone());
        LocalDateTime fromLdt = LocalDateTime.parse(request.fromLocal());
        LocalDateTime toLdt = LocalDateTime.parse(request.toLocal());
        Instant fromInstant = fromLdt.atZone(zoneId).toInstant();
        Instant toInstant = toLdt.atZone(zoneId).toInstant();

        // 1. Build MD5 Cache Key
        List<String> sortedUserIds = request.userIds().stream()
                .map(UUID::toString)
                .sorted()
                .toList();

        String rawSignature = sortedUserIds + "|" + request.durationMinutes() + "|" + fromInstant.toString() + "|" + toInstant.toString();
        String md5Hash = DigestUtils.md5DigestAsHex(rawSignature.getBytes(StandardCharsets.UTF_8));
        String cacheKey = "free-slots:" + md5Hash;

        // 2. Check Redis Cache
        try {
            String cachedVal = redisTemplate.opsForValue().get(cacheKey);
            if (cachedVal != null) {
                log.info("Redis cache hit for key: {}", cacheKey);
                return objectMapper.readValue(cachedVal, FreeSlotResponse.class);
            }
        } catch (Exception e) {
            log.error("Failed to query Redis cache", e);
        }

        // 3. Fetch busy slots from Event Service
        BulkBusySlotsRequest bulkRequest = new BulkBusySlotsRequest(request.userIds(), fromInstant, toInstant);
        Map<String, List<TimeSlot>> userBusySlots = eventServiceClient.getBulkBusySlots(bulkRequest);

        List<TimeSlot> allBusySlots = new ArrayList<>();
        if (userBusySlots != null) {
            for (List<TimeSlot> slots : userBusySlots.values()) {
                allBusySlots.addAll(slots);
            }
        }

        // 4. Merge overlapping busy slots
        List<TimeSlot> mergedSlots = intervalMerger.merge(allBusySlots);

        // 5. Calculate free space gaps
        List<TimeSlot> gaps = gapFinder.findGaps(mergedSlots, fromInstant, toInstant, request.durationMinutes());

        // 6. Convert back to local representations
        List<FreeSlot> freeSlots = new ArrayList<>();
        for (TimeSlot slot : gaps) {
            ZonedDateTime localStart = slot.start().atZone(zoneId);
            ZonedDateTime localEnd = slot.end().atZone(zoneId);
            freeSlots.add(new FreeSlot(
                    localStart.toLocalDateTime().toString(),
                    localEnd.toLocalDateTime().toString(),
                    (int) Duration.between(slot.start(), slot.end()).toMinutes()
            ));
        }

        FreeSlotResponse response = new FreeSlotResponse(freeSlots, freeSlots.size());

        // 7. Write to Redis with 5-minute TTL
        try {
            String jsonVal = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, jsonVal, Duration.ofMinutes(5));
            log.info("Cached free slots in Redis for key: {}", cacheKey);
        } catch (Exception e) {
            log.error("Failed to serialize and write response to Redis cache", e);
        }

        return response;
    }

    public ConflictCheckResponse checkConflicts(ConflictCheckRequest request) {
        log.info("Checking availability conflicts for users size: {} in time window [{} - {}]", 
                request.userIds() == null ? 0 : request.userIds().size(), request.startTime(), request.endTime());

        if (request.userIds() == null || request.userIds().isEmpty()) {
            return new ConflictCheckResponse(false, Collections.emptyList());
        }

        BulkBusySlotsRequest bulkRequest = new BulkBusySlotsRequest(request.userIds(), request.startTime(), request.endTime());
        Map<String, List<TimeSlot>> userBusySlots = eventServiceClient.getBulkBusySlots(bulkRequest);

        List<ConflictDetail> conflicts = new ArrayList<>();
        if (userBusySlots != null) {
            for (Map.Entry<String, List<TimeSlot>> entry : userBusySlots.entrySet()) {
                UUID userId = UUID.fromString(entry.getKey());
                List<TimeSlot> slots = entry.getValue();
                for (TimeSlot slot : slots) {
                    // Overlap check
                    if (slot.start().isBefore(request.endTime()) && slot.end().isAfter(request.startTime())) {
                        conflicts.add(new ConflictDetail(
                                userId,
                                null, // Event ID is anonymized in this slot view
                                "Conflicting Meeting Slot",
                                slot.start(),
                                slot.end()
                        ));
                    }
                }
            }
        }

        boolean hasConflict = !conflicts.isEmpty();
        return new ConflictCheckResponse(hasConflict, conflicts);
    }
}
