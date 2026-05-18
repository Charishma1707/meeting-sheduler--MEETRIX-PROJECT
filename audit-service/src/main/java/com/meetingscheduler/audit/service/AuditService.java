package com.meetingscheduler.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingscheduler.audit.dto.KafkaEventMessage;
import com.meetingscheduler.audit.entity.AuditLog;
import com.meetingscheduler.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void processAuditMessage(String message) {
        log.info("Processing audit message: {}", message);
        try {
            KafkaEventMessage eventMessage = objectMapper.readValue(message, KafkaEventMessage.class);

            String action = eventMessage.eventType();
            UUID actorUserId = eventMessage.actorUserId();
            UUID eventId = eventMessage.eventId();
            Instant timestamp = eventMessage.timestamp();
            if (timestamp == null) {
                timestamp = Instant.now();
            }

            String serviceName = "event-service";
            if (action != null && (action.contains("REMINDER") || action.contains("reminder"))) {
                serviceName = "reminder-service";
            }

            AuditLog logEntity = AuditLog.builder()
                    .eventId(eventId)
                    .actorUserId(actorUserId)
                    .action(action)
                    .payload(message)
                    .serviceName(serviceName)
                    .timestamp(timestamp)
                    .build();

            auditLogRepository.save(logEntity);
            log.info("Successfully persisted audit log for action: {}", action);
        } catch (Exception e) {
            log.error("Failed to deserialize audit message, skipping. Message: {}", message, e);
        }
    }

    public List<AuditLog> getAuditForEvent(UUID eventId) {
        log.info("Fetching audit logs for event ID: {}", eventId);
        return auditLogRepository.findByEventIdOrderByTimestampAsc(eventId);
    }

    public Page<AuditLog> getAuditForUser(UUID userId, Pageable pageable) {
        log.info("Fetching paged audit logs for actor user ID: {}", userId);
        return auditLogRepository.findByActorUserIdOrderByTimestampDesc(userId, pageable);
    }

    public Page<AuditLog> getFilteredAuditLogs(Instant from, Instant to, String action, Pageable pageable) {
        log.info("Fetching filtered audit logs between {} and {}, action filter: {}", from, to, action);
        String actionQuery = (action == null) ? "" : action;
        return auditLogRepository.findByTimestampBetweenAndActionContaining(from, to, actionQuery, pageable);
    }
}
