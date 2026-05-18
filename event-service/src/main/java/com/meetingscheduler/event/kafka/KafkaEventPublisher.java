package com.meetingscheduler.event.kafka;

import com.meetingscheduler.event.entity.Event;
import com.meetingscheduler.event.entity.EventInvite;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void publishMeetingCreated(Event event, List<UUID> inviteeIds) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventId", event.getId().toString());
            payload.put("organizerId", event.getOrganizerId().toString());
            payload.put("inviteeIds", inviteeIds.stream().map(UUID::toString).toList());
            payload.put("startTime", event.getStartTime().toString());
            payload.put("timezone", event.getTimezone());

            String jsonPayload = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send("meeting.created", event.getId().toString(), jsonPayload);
            log.info("Published meeting.created event: {}", jsonPayload);

            publishAuditLog("MEETING_CREATED", event.getOrganizerId(), "Created event: " + event.getTitle());
        } catch (Exception e) {
            log.error("Failed to publish meeting.created event", e);
        }
    }

    public void publishMeetingUpdated(Event event, List<UUID> inviteeIds) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventId", event.getId().toString());
            payload.put("organizerId", event.getOrganizerId().toString());
            payload.put("inviteeIds", inviteeIds.stream().map(UUID::toString).toList());
            payload.put("startTime", event.getStartTime().toString());
            payload.put("timezone", event.getTimezone());

            String jsonPayload = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send("meeting.updated", event.getId().toString(), jsonPayload);
            log.info("Published meeting.updated event: {}", jsonPayload);

            publishAuditLog("MEETING_UPDATED", event.getOrganizerId(), "Updated event: " + event.getTitle());
        } catch (Exception e) {
            log.error("Failed to publish meeting.updated event", e);
        }
    }

    public void publishMeetingCancelled(Event event, List<UUID> inviteeIds) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventId", event.getId().toString());
            payload.put("organizerId", event.getOrganizerId().toString());
            payload.put("inviteeIds", inviteeIds.stream().map(UUID::toString).toList());

            String jsonPayload = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send("meeting.cancelled", event.getId().toString(), jsonPayload);
            log.info("Published meeting.cancelled event: {}", jsonPayload);

            publishAuditLog("MEETING_CANCELLED", event.getOrganizerId(), "Cancelled event: " + event.getTitle());
        } catch (Exception e) {
            log.error("Failed to publish meeting.cancelled event", e);
        }
    }

    public void publishRsvpUpdated(EventInvite invite, Event event) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("inviteId", invite.getId().toString());
            payload.put("eventId", event.getId().toString());
            payload.put("inviteeId", invite.getInviteeId().toString());
            payload.put("status", invite.getStatus().toString());
            payload.put("respondedAt", invite.getRespondedAt() != null ? invite.getRespondedAt().toString() : null);

            String jsonPayload = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send("rsvp.updated", invite.getId().toString(), jsonPayload);
            log.info("Published rsvp.updated event: {}", jsonPayload);

            publishAuditLog("RSVP_UPDATED", invite.getInviteeId(), "RSVP set to " + invite.getStatus() + " for event " + event.getId());
        } catch (Exception e) {
            log.error("Failed to publish rsvp.updated event", e);
        }
    }

    public void publishAuditLog(String action, UUID actorUserId, String details) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("action", action);
            payload.put("actorUserId", actorUserId.toString());
            payload.put("timestamp", Instant.now().toString());
            payload.put("details", details);

            String jsonPayload = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send("audit.log", actorUserId.toString(), jsonPayload);
            log.info("Published audit.log event: {}", jsonPayload);
        } catch (Exception e) {
            log.error("Failed to publish audit.log event", e);
        }
    }
}
