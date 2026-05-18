package com.meetingscheduler.event.controller;

import com.meetingscheduler.event.dto.*;
import com.meetingscheduler.event.entity.InviteStatus;
import com.meetingscheduler.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("HTTP POST /api/events by user: {}", userId);
        EventResponse response = eventService.createEvent(request, UUID.fromString(userId));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(
            @PathVariable("id") UUID id,
            @RequestHeader("X-User-Id") String userId) {
        log.info("HTTP GET /api/events/{} by user: {}", id, userId);
        EventResponse response = eventService.getEvent(id, UUID.fromString(userId));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateEventRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("HTTP PUT /api/events/{} by user: {}", id, userId);
        EventResponse response = eventService.updateEvent(id, request, UUID.fromString(userId));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(
            @PathVariable("id") UUID id,
            @RequestHeader("X-User-Id") String userId) {
        log.info("HTTP DELETE /api/events/{} by user: {}", id, userId);
        eventService.deleteEvent(id, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    public ResponseEntity<List<EventResponse>> getMyEvents(
            @RequestParam("from") String fromStr,
            @RequestParam("to") String toStr,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestHeader("X-User-Id") String userId) {
        log.info("HTTP GET /api/events/my for user: {}", userId);
        Instant from = Instant.parse(fromStr);
        Instant to = Instant.parse(toStr);
        Pageable pageable = PageRequest.of(page, size, Sort.by("startTime").ascending());
        List<EventResponse> response = eventService.getMyEvents(UUID.fromString(userId), from, to, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/rsvp")
    public ResponseEntity<EventResponse> rsvp(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RsvpRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("HTTP POST /api/events/{}/rsvp status: {} by user: {}", id, request.status(), userId);
        EventResponse response = eventService.rsvp(id, UUID.fromString(userId), request.status());
        return ResponseEntity.ok(response);
    }

    // INTERNAL ENDPOINTS

    @PostMapping("/internal/bulk-busy-slots")
    public ResponseEntity<Map<String, List<TimeSlot>>> getBulkBusySlots(
            @Valid @RequestBody BulkBusySlotsRequest request) {
        log.info("HTTP POST /api/events/internal/bulk-busy-slots for users size: {}", request.userIds().size());
        Map<String, List<TimeSlot>> response = eventService.getBulkBusySlots(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/internal/upcoming")
    public ResponseEntity<List<EventResponse>> getUpcomingEvents(
            @RequestParam(value = "withinMinutes", defaultValue = "15") int withinMinutes) {
        log.info("HTTP GET /api/events/internal/upcoming withinMinutes: {}", withinMinutes);
        List<EventResponse> response = eventService.getUpcomingEvents(withinMinutes);
        return ResponseEntity.ok(response);
    }
}
