package com.meetingscheduler.availability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingscheduler.availability.algorithm.GapFinder;
import com.meetingscheduler.availability.algorithm.IntervalMerger;
import com.meetingscheduler.availability.client.EventServiceClient;
import com.meetingscheduler.availability.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AvailabilityServiceTest {

    @Mock
    private EventServiceClient eventServiceClient;

    @Mock
    private IntervalMerger intervalMerger;

    @Mock
    private GapFinder gapFinder;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AvailabilityService availabilityService;

    @BeforeEach
    public void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    public void findFreeSlots_cacheHit_returnsFromCacheWithoutCallingEventService() throws Exception {
        FreeSlotRequest request = new FreeSlotRequest(
                List.of(UUID.randomUUID()),
                60,
                "2025-06-02T10:00:00",
                "2025-06-02T12:00:00",
                "Asia/Kolkata"
        );

        FreeSlotResponse expectedResponse = new FreeSlotResponse(
                List.of(new FreeSlot("2025-06-02T10:00:00", "2025-06-02T12:00:00", 120)),
                1
        );

        when(valueOps.get(anyString())).thenReturn("cached-json-string");
        when(objectMapper.readValue(anyString(), eq(FreeSlotResponse.class))).thenReturn(expectedResponse);

        FreeSlotResponse result = availabilityService.getFreeSlots(request);

        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(eventServiceClient, never()).getBulkBusySlots(any());
    }

    @Test
    public void findFreeSlots_cacheMiss_callsEventServiceAndCachesResult() throws Exception {
        UUID userId = UUID.randomUUID();
        FreeSlotRequest request = new FreeSlotRequest(
                List.of(userId),
                60,
                "2025-06-02T10:00:00",
                "2025-06-02T15:00:00",
                "Asia/Kolkata"
        );

        when(valueOps.get(anyString())).thenReturn(null);

        Map<String, List<TimeSlot>> mockBusy = new HashMap<>();
        mockBusy.put(userId.toString(), List.of(new TimeSlot(
                Instant.parse("2025-06-02T06:30:00Z"),
                Instant.parse("2025-06-02T07:30:00Z")
        )));
        when(eventServiceClient.getBulkBusySlots(any())).thenReturn(mockBusy);
        when(intervalMerger.merge(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<TimeSlot> mockGaps = List.of(
                new TimeSlot(Instant.parse("2025-06-02T04:30:00Z"), Instant.parse("2025-06-02T06:30:00Z")),
                new TimeSlot(Instant.parse("2025-06-02T07:30:00Z"), Instant.parse("2025-06-02T09:30:00Z"))
        );
        when(gapFinder.findGaps(anyList(), any(), any(), anyInt())).thenReturn(mockGaps);

        FreeSlotResponse expectedResponse = new FreeSlotResponse(
                List.of(
                        new FreeSlot("2025-06-02T10:00", "2025-06-02T12:00", 120),
                        new FreeSlot("2025-06-02T13:00", "2025-06-02T15:00", 120)
                ),
                2
        );
        when(objectMapper.writeValueAsString(any())).thenReturn("some-serialized-json");

        FreeSlotResponse result = availabilityService.getFreeSlots(request);

        assertNotNull(result);
        assertEquals(expectedResponse.totalFound(), result.totalFound());
        verify(eventServiceClient, times(1)).getBulkBusySlots(any());
        verify(valueOps, times(1)).set(anyString(), eq("some-serialized-json"), eq(Duration.ofMinutes(5)));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void findFreeSlots_multipleUsers_mergesAllBusySlots() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        FreeSlotRequest request = new FreeSlotRequest(
                List.of(user1, user2),
                60,
                "2025-06-02T10:00:00",
                "2025-06-02T15:00:00",
                "Asia/Kolkata"
        );

        when(valueOps.get(anyString())).thenReturn(null);

        Map<String, List<TimeSlot>> mockBusy = new HashMap<>();
        mockBusy.put(user1.toString(), List.of(new TimeSlot(
                Instant.parse("2025-06-02T06:30:00Z"),
                Instant.parse("2025-06-02T07:30:00Z")
        )));
        mockBusy.put(user2.toString(), List.of(new TimeSlot(
                Instant.parse("2025-06-02T07:00:00Z"),
                Instant.parse("2025-06-02T08:00:00Z")
        )));
        when(eventServiceClient.getBulkBusySlots(any())).thenReturn(mockBusy);

        availabilityService.getFreeSlots(request);

        ArgumentCaptor<List<TimeSlot>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(intervalMerger).merge(listCaptor.capture());

        List<TimeSlot> capturedList = listCaptor.getValue();
        assertEquals(2, capturedList.size());
    }

    @Test
    public void checkConflicts_noOverlap_returnsHasConflictFalse() {
        UUID userId = UUID.randomUUID();
        ConflictCheckRequest request = new ConflictCheckRequest(
                List.of(userId),
                Instant.parse("2025-06-02T10:00:00Z"),
                Instant.parse("2025-06-02T11:00:00Z")
        );

        Map<String, List<TimeSlot>> mockBusy = new HashMap<>();
        // Busy slot completely outside check window (12:00 - 13:00)
        mockBusy.put(userId.toString(), List.of(new TimeSlot(
                Instant.parse("2025-06-02T12:00:00Z"),
                Instant.parse("2025-06-02T13:00:00Z")
        )));
        when(eventServiceClient.getBulkBusySlots(any())).thenReturn(mockBusy);

        ConflictCheckResponse response = availabilityService.checkConflicts(request);

        assertFalse(response.hasConflict());
        assertTrue(response.conflicts().isEmpty());
    }

    @Test
    public void checkConflicts_withOverlap_returnsConflictDetails() {
        UUID userId = UUID.randomUUID();
        ConflictCheckRequest request = new ConflictCheckRequest(
                List.of(userId),
                Instant.parse("2025-06-02T10:00:00Z"),
                Instant.parse("2025-06-02T11:00:00Z")
        );

        Map<String, List<TimeSlot>> mockBusy = new HashMap<>();
        // Overlap: 10:30 - 11:30
        mockBusy.put(userId.toString(), List.of(new TimeSlot(
                Instant.parse("2025-06-02T10:30:00Z"),
                Instant.parse("2025-06-02T11:30:00Z")
        )));
        when(eventServiceClient.getBulkBusySlots(any())).thenReturn(mockBusy);

        ConflictCheckResponse response = availabilityService.checkConflicts(request);

        assertTrue(response.hasConflict());
        assertEquals(1, response.conflicts().size());
        assertEquals(userId, response.conflicts().get(0).userId());
        assertEquals(Instant.parse("2025-06-02T10:30:00Z"), response.conflicts().get(0).conflictStart());
    }

    @Test
    public void checkConflicts_emptyUserList_returnsNoConflict_withoutCallingEventService() {
        ConflictCheckRequest request = new ConflictCheckRequest(
                Collections.emptyList(),
                Instant.parse("2025-06-02T10:00:00Z"),
                Instant.parse("2025-06-02T11:00:00Z")
        );

        ConflictCheckResponse response = availabilityService.checkConflicts(request);

        assertFalse(response.hasConflict());
        assertTrue(response.conflicts().isEmpty());
        verify(eventServiceClient, never()).getBulkBusySlots(any());
    }
}
