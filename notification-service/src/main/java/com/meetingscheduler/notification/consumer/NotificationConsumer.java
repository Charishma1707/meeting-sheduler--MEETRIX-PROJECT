package com.meetingscheduler.notification.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingscheduler.notification.client.UserServiceClient;
import com.meetingscheduler.notification.dto.BatchUserRequest;
import com.meetingscheduler.notification.dto.UserProfileResponse;
import com.meetingscheduler.notification.service.AsyncNotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final UserServiceClient userServiceClient;
    private final AsyncNotificationSender asyncNotificationSender;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE
    )
    @KafkaListener(topics = "meeting.created", groupId = "notification-group")
    @SuppressWarnings("unchecked")
    public void consumeMeetingCreated(String message) {
        log.info("Received meeting.created event: {}", message);
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
            UUID eventId = UUID.fromString((String) payload.get("eventId"));
            String title = (String) payload.get("title");
            UUID organizerId = UUID.fromString((String) payload.get("organizerId"));
            List<UUID> inviteeIds = ((List<String>) payload.get("inviteeIds")).stream()
                    .map(UUID::fromString)
                    .toList();
            String startTime = (String) payload.get("startTime");
            String timezone = (String) payload.get("timezone");

            // Fetch users batch
            List<UUID> allUserIds = new ArrayList<>(inviteeIds);
            allUserIds.add(organizerId);

            List<UserProfileResponse> profiles = userServiceClient.getProfilesBatch(new BatchUserRequest(allUserIds));
            Map<UUID, UserProfileResponse> profileMap = profiles.stream()
                    .collect(Collectors.toMap(UserProfileResponse::id, p -> p));

            UserProfileResponse organizer = profileMap.get(organizerId);
            String organizerName = (organizer != null) ? organizer.name() : "Organizer";

            for (UUID inviteeId : inviteeIds) {
                UserProfileResponse invitee = profileMap.get(inviteeId);
                if (invitee == null) continue;

                asyncNotificationSender.sendMeetingCreatedAsync(invitee, eventId, title, organizerName, startTime, timezone);
            }
        } catch (Exception e) {
            log.error("Error processing meeting.created event", e);
            throw new RuntimeException(e);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE
    )
    @KafkaListener(topics = "meeting.cancelled", groupId = "notification-group")
    @SuppressWarnings("unchecked")
    public void consumeMeetingCancelled(String message) {
        log.info("Received meeting.cancelled event: {}", message);
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
            UUID eventId = UUID.fromString((String) payload.get("eventId"));
            String title = (String) payload.get("title");
            List<UUID> inviteeIds = ((List<String>) payload.get("inviteeIds")).stream()
                    .map(UUID::fromString)
                    .toList();

            List<UserProfileResponse> profiles = userServiceClient.getProfilesBatch(new BatchUserRequest(inviteeIds));
            Map<UUID, UserProfileResponse> profileMap = profiles.stream()
                    .collect(Collectors.toMap(UserProfileResponse::id, p -> p));

            for (UUID inviteeId : inviteeIds) {
                UserProfileResponse invitee = profileMap.get(inviteeId);
                if (invitee == null) continue;

                asyncNotificationSender.sendMeetingCancelledAsync(invitee, eventId, title);
            }
        } catch (Exception e) {
            log.error("Error processing meeting.cancelled event", e);
            throw new RuntimeException(e);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE
    )
    @KafkaListener(topics = "meeting.updated", groupId = "notification-group")
    @SuppressWarnings("unchecked")
    public void consumeMeetingUpdated(String message) {
        log.info("Received meeting.updated event: {}", message);
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
            UUID eventId = UUID.fromString((String) payload.get("eventId"));
            String title = (String) payload.get("title");
            List<UUID> inviteeIds = ((List<String>) payload.get("inviteeIds")).stream()
                    .map(UUID::fromString)
                    .toList();

            List<UserProfileResponse> profiles = userServiceClient.getProfilesBatch(new BatchUserRequest(inviteeIds));
            Map<UUID, UserProfileResponse> profileMap = profiles.stream()
                    .collect(Collectors.toMap(UserProfileResponse::id, p -> p));

            for (UUID inviteeId : inviteeIds) {
                UserProfileResponse invitee = profileMap.get(inviteeId);
                if (invitee == null) continue;

                asyncNotificationSender.sendMeetingUpdatedAsync(invitee, eventId, title);
            }
        } catch (Exception e) {
            log.error("Error processing meeting.updated event", e);
            throw new RuntimeException(e);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE
    )
    @KafkaListener(topics = "rsvp.updated", groupId = "notification-group")
    public void consumeRsvpUpdated(String message) {
        log.info("Received rsvp.updated event: {}", message);
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
            UUID eventId = UUID.fromString((String) payload.get("eventId"));
            UUID inviteeId = UUID.fromString((String) payload.get("inviteeId"));
            UUID organizerId = UUID.fromString((String) payload.get("organizerId"));
            String status = (String) payload.get("status");

            List<UserProfileResponse> profiles = userServiceClient.getProfilesBatch(new BatchUserRequest(List.of(inviteeId, organizerId)));
            Map<UUID, UserProfileResponse> profileMap = profiles.stream()
                    .collect(Collectors.toMap(UserProfileResponse::id, p -> p));

            UserProfileResponse invitee = profileMap.get(inviteeId);
            UserProfileResponse organizer = profileMap.get(organizerId);

            if (organizer != null && invitee != null) {
                asyncNotificationSender.sendRsvpUpdatedAsync(organizer, invitee, eventId, status);
            }
        } catch (Exception e) {
            log.error("Error processing rsvp.updated event", e);
            throw new RuntimeException(e);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE
    )
    @KafkaListener(topics = "reminder.trigger", groupId = "notification-group")
    @SuppressWarnings("unchecked")
    public void consumeReminderTrigger(String message) {
        log.info("Received reminder.trigger event: {}", message);
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
            UUID eventId = UUID.fromString((String) payload.get("eventId"));
            
            Map<String, Object> innerPayload = (Map<String, Object>) payload.get("payload");
            String title = "";
            List<UUID> attendeeIds = Collections.emptyList();
            if (innerPayload != null) {
                title = (String) innerPayload.get("title");
                List<String> inviteeIds = (List<String>) innerPayload.get("inviteeIds");
                if (inviteeIds != null) {
                    attendeeIds = inviteeIds.stream()
                            .map(UUID::fromString)
                            .toList();
                }
            }

            List<UserProfileResponse> profiles = userServiceClient.getProfilesBatch(new BatchUserRequest(attendeeIds));
            Map<UUID, UserProfileResponse> profileMap = profiles.stream()
                    .collect(Collectors.toMap(UserProfileResponse::id, p -> p));

            for (UUID attendeeId : attendeeIds) {
                UserProfileResponse attendee = profileMap.get(attendeeId);
                if (attendee == null) continue;

                asyncNotificationSender.sendReminderAsync(attendee, eventId, title);
            }
        } catch (Exception e) {
            log.error("Error processing reminder.trigger event", e);
            throw new RuntimeException(e);
        }
    }

    @DltHandler
    public void handleDlt(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Event from topic {} failed after retries and landed in DLT. Message payload: {}", topic, message);
    }
}
