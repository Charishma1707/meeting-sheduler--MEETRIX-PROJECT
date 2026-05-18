package com.meetingscheduler.availability.controller;

import com.meetingscheduler.availability.dto.*;
import com.meetingscheduler.availability.service.AvailabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
@Slf4j
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @PostMapping("/free-slots")
    public ResponseEntity<FreeSlotResponse> getFreeSlots(
            @Valid @RequestBody FreeSlotRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        log.info("HTTP POST /api/availability/free-slots requested by user: {}", userId);
        FreeSlotResponse response = availabilityService.getFreeSlots(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/internal/check-conflicts")
    public ResponseEntity<ConflictCheckResponse> checkConflicts(
            @Valid @RequestBody ConflictCheckRequest request) {
        log.info("HTTP POST /api/availability/internal/check-conflicts invoked internally");
        ConflictCheckResponse response = availabilityService.checkConflicts(request);
        return ResponseEntity.ok(response);
    }
}
