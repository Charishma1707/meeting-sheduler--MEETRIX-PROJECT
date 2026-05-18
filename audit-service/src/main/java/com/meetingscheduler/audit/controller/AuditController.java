package com.meetingscheduler.audit.controller;

import com.meetingscheduler.audit.entity.AuditLog;
import com.meetingscheduler.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/events/{eventId}")
    public ResponseEntity<List<AuditLog>> getAuditForEvent(@PathVariable("eventId") UUID eventId) {
        log.info("HTTP GET /api/audit/events/{}", eventId);
        List<AuditLog> logs = auditService.getAuditForEvent(eventId);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<Page<AuditLog>> getAuditForUser(
            @PathVariable("userId") UUID userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        log.info("HTTP GET /api/audit/users/{} (page: {}, size: {})", userId, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> logs = auditService.getAuditForUser(userId, pageable);
        return ResponseEntity.ok(logs);
    }

    @GetMapping
    public ResponseEntity<Page<AuditLog>> getFilteredAuditLogs(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        log.info("HTTP GET /api/audit (from: {}, to: {}, action: {}, page: {}, size: {})", fromStr, toStr, action, page, size);

        Instant from = (fromStr == null || fromStr.isBlank()) ? Instant.EPOCH : Instant.parse(fromStr);
        Instant to = (toStr == null || toStr.isBlank()) ? Instant.now().plus(365, ChronoUnit.DAYS) : Instant.parse(toStr);

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<AuditLog> logs = auditService.getFilteredAuditLogs(from, to, action, pageable);
        return ResponseEntity.ok(logs);
    }
}
