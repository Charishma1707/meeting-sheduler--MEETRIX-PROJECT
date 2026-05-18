package com.meetingscheduler.notification.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingscheduler.notification.client.UserServiceClient;
import com.meetingscheduler.notification.dto.BatchUserRequest;
import com.meetingscheduler.notification.dto.NotificationPayload;
import com.meetingscheduler.notification.dto.NotificationPreference;
import com.meetingscheduler.notification.dto.UserProfileResponse;
import com.meetingscheduler.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final UserServiceClient userServiceClient;
    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;
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

                NotificationPreference preference = invitee.notificationPreference();

                if (preference == NotificationPreference.IN_APP || preference == NotificationPreference.BOTH) {
                    NotificationPayload wsPayload = new NotificationPayload(
                            "INVITE_RECEIVED",
                            "New invite",
                            organizerName + " invited you to " + title,
                            eventId,
                            Instant.now()
                    );
                    messagingTemplate.convertAndSendToUser(inviteeId.toString(), "/queue/notifications", wsPayload);
                }

                if (preference == NotificationPreference.EMAIL || preference == NotificationPreference.BOTH) {
                    String emailBody = String.format(
                            "Hi %s,\n\nYou have been invited to a new meeting: '%s'.\nOrganizer: %s\nStart Time: %s (%s)\n\nPlease log in to respond.",
                            invitee.name(), title, organizerName, startTime, timezone
                    );
                    emailService.send(invitee.email(), "Meeting Invite: " + title, emailBody);
                }
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

                NotificationPreference preference = invitee.notificationPreference();

                if (preference == NotificationPreference.IN_APP || preference == NotificationPreference.BOTH) {
                    NotificationPayload wsPayload = new NotificationPayload(
                            "MEETING_CANCELLED",
                            "Meeting Cancelled",
                            "Meeting has been cancelled: " + title,
                            eventId,
                            Instant.now()
                    );
                    messagingTemplate.convertAndSendToUser(inviteeId.toString(), "/queue/notifications", wsPayload);
                }

                if (preference == NotificationPreference.EMAIL || preference == NotificationPreference.BOTH) {
                    String emailBody = String.format("Hi %s,\n\nThe meeting '%s' has been cancelled.", invitee.name(), title);
                    emailService.send(invitee.email(), "Meeting Cancelled: " + title, emailBody);
                }
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

                NotificationPreference preference = invitee.notificationPreference();

                if (preference == NotificationPreference.IN_APP || preference == NotificationPreference.BOTH) {
                    NotificationPayload wsPayload = new NotificationPayload(
                            "MEETING_UPDATED",
                            "Meeting Updated",
                            "Meeting details updated: " + title,
                            eventId,
                            Instant.now()
                    );
                    messagingTemplate.convertAndSendToUser(inviteeId.toString(), "/queue/notifications", wsPayload);
                }

                if (preference == NotificationPreference.EMAIL || preference == NotificationPreference.BOTH) {
                    String emailBody = String.format("Hi %s,\n\nThe details for the meeting '%s' have been updated.", invitee.name(), title);
                    emailService.send(invitee.email(), "Meeting Updated: " + title, emailBody);
                }
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
                NotificationPreference preference = organizer.notificationPreference();

                if (preference == NotificationPreference.IN_APP || preference == NotificationPreference.BOTH) {
                    NotificationPayload wsPayload = new NotificationPayload(
                            "RSVP_UPDATE",
                            "RSVP Response Received",
                            invitee.name() + " has " + status + " your invite.",
                            eventId,
                            Instant.now()
                    );
                    messagingTemplate.convertAndSendToUser(organizerId.toString(), "/queue/notifications", wsPayload);
                }

                if (preference == NotificationPreference.EMAIL || preference == NotificationPreference.BOTH) {
                    String emailBody = String.format("Hi %s,\n\n%s has %s your meeting invitation.", organizer.name(), invitee.name(), status);
                    emailService.send(organizer.email(), "RSVP Update: " + invitee.name(), emailBody);
                }
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
            String title = (String) payload.get("title");
            List<UUID> attendeeIds = ((List<String>) payload.get("attendeeIds")).stream()
                    .map(UUID::fromString)
                    .toList();

            List<UserProfileResponse> profiles = userServiceClient.getProfilesBatch(new BatchUserRequest(attendeeIds));
            Map<UUID, UserProfileResponse> profileMap = profiles.stream()
                    .collect(Collectors.toMap(UserProfileResponse::id, p -> p));

            for (UUID attendeeId : attendeeIds) {
                UserProfileResponse attendee = profileMap.get(attendeeId);
                if (attendee == null) continue;

                NotificationPreference preference = attendee.notificationPreference();

                if (preference == NotificationPreference.IN_APP || preference == NotificationPreference.BOTH) {
                    NotificationPayload wsPayload = new NotificationPayload(
                            "REMINDER",
                            "Meeting Reminder",
                            "Meeting: " + title + " starts in 15 minutes.",
                            eventId,
                            Instant.now()
                    );
                    messagingTemplate.convertAndSendToUser(attendeeId.toString(), "/queue/notifications", wsPayload);
                }

                if (preference == NotificationPreference.EMAIL || preference == NotificationPreference.BOTH) {
                    String emailBody = String.format("Hi %s,\n\nYour meeting '%s' starts in 15 minutes.", attendee.name(), title);
                    emailService.send(attendee.email(), "Meeting Reminder: " + title, emailBody);
                }
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
