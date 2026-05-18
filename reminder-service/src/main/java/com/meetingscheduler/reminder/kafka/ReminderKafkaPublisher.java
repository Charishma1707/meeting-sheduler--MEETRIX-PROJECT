package com.meetingscheduler.reminder.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingscheduler.reminder.dto.UpcomingEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void publishReminder(UpcomingEventDto event) {
        try {
            Map<String, Object> reminderPayload = new HashMap<>();
            reminderPayload.put("eventId", event.eventId().toString());
            reminderPayload.put("eventType", "REMINDER_TRIGGER");
            reminderPayload.put("actorUserId", event.organizerId().toString());
            reminderPayload.put("timestamp", Instant.now().toString());

            Map<String, Object> innerPayload = new HashMap<>();
            innerPayload.put("title", event.title());
            innerPayload.put("inviteeIds", event.inviteeIds().stream().map(Object::toString).toList());
            innerPayload.put("startTime", event.startTime().toString());

            reminderPayload.put("payload", innerPayload);

            String jsonVal = objectMapper.writeValueAsString(reminderPayload);
            kafkaTemplate.send("reminder.trigger", event.eventId().toString(), jsonVal);
            log.info("Published reminder.trigger for event: {}", event.eventId());
        } catch (Exception e) {
            log.error("Failed to publish reminder.trigger for event: " + event.eventId(), e);
        }
    }

    public void publishAudit(UpcomingEventDto event) {
        try {
            Map<String, Object> auditPayload = new HashMap<>();
            auditPayload.put("eventType", "REMINDER_SENT");
            auditPayload.put("actorUserId", event.organizerId().toString());
            auditPayload.put("eventId", event.eventId().toString());
            auditPayload.put("timestamp", Instant.now().toString());

            String jsonVal = objectMapper.writeValueAsString(auditPayload);
            kafkaTemplate.send("audit.log", event.organizerId().toString(), jsonVal);
            log.info("Published reminder audit.log for event: {}", event.eventId());
        } catch (Exception e) {
            log.error("Failed to publish reminder audit.log for event: " + event.eventId(), e);
        }
    }
}
