package com.meetingscheduler.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingscheduler.audit.dto.KafkaEventMessage;
import com.meetingscheduler.audit.entity.AuditLog;
import com.meetingscheduler.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditService auditService;

    @Test
    public void processAuditMessage_validMessage_savesAuditLog() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        Instant now = Instant.now();

        KafkaEventMessage message = new KafkaEventMessage(
                eventId, "MEETING_CREATED", actorUserId, now, "payload-content"
        );

        when(objectMapper.readValue(anyString(), eq(KafkaEventMessage.class))).thenReturn(message);

        auditService.processAuditMessage("dummy-json");

        verify(auditLogRepository, times(1)).save(argThat(log ->
                "MEETING_CREATED".equals(log.getAction()) &&
                actorUserId.equals(log.getActorUserId()) &&
                eventId.equals(log.getEventId()) &&
                "event-service".equals(log.getServiceName())
        ));
    }

    @Test
    public void processAuditMessage_malformedJson_doesNotThrowAndDoesNotSave() throws Exception {
        JsonProcessingException mockEx = mock(JsonProcessingException.class);
        when(objectMapper.readValue(anyString(), eq(KafkaEventMessage.class))).thenThrow(mockEx);

        assertThatNoException().isThrownBy(() -> {
            auditService.processAuditMessage("malformed-json");
        });

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    public void getAuditForEvent_existingEvent_returnsSortedList() {
        UUID eventId = UUID.randomUUID();
        AuditLog mockLog = AuditLog.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .action("MEETING_CREATED")
                .serviceName("event-service")
                .timestamp(Instant.now())
                .build();

        when(auditLogRepository.findByEventIdOrderByTimestampAsc(eventId)).thenReturn(List.of(mockLog));

        List<AuditLog> result = auditService.getAuditForEvent(eventId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("MEETING_CREATED");
    }

    @Test
    public void getAuditForEvent_noEntries_returnsEmptyList() {
        UUID eventId = UUID.randomUUID();
        when(auditLogRepository.findByEventIdOrderByTimestampAsc(eventId)).thenReturn(List.of());

        List<AuditLog> result = auditService.getAuditForEvent(eventId);

        assertThat(result).isEmpty();
    }

    @Test
    public void getAuditForUser_returnsPagedResults() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10, Sort.by("timestamp").descending());

        AuditLog log1 = AuditLog.builder().id(UUID.randomUUID()).actorUserId(userId).action("RSVP_UPDATED").timestamp(Instant.now()).serviceName("event-service").build();
        AuditLog log2 = AuditLog.builder().id(UUID.randomUUID()).actorUserId(userId).action("MEETING_UPDATED").timestamp(Instant.now()).serviceName("event-service").build();

        Page<AuditLog> mockPage = new PageImpl<>(List.of(log1, log2), pageable, 2);
        when(auditLogRepository.findByActorUserIdOrderByTimestampDesc(userId, pageable)).thenReturn(mockPage);

        Page<AuditLog> result = auditService.getAuditForUser(userId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
    }
}
