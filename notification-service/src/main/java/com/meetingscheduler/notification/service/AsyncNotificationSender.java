package com.meetingscheduler.notification.service;

import com.meetingscheduler.notification.dto.NotificationPayload;
import com.meetingscheduler.notification.dto.NotificationPreference;
import com.meetingscheduler.notification.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncNotificationSender {

    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;

    @Async("notificationTaskExecutor")
    public void sendMeetingCreatedAsync(UserProfileResponse invitee, UUID eventId, String title, String organizerName, String startTime, String timezone) {
        log.debug("Sending meeting created notification asynchronously to user: {} on thread: {}", invitee.id(), Thread.currentThread().getName());
        try {
            NotificationPreference preference = invitee.notificationPreference();

            if (preference == NotificationPreference.IN_APP || preference == NotificationPreference.BOTH) {
                NotificationPayload wsPayload = new NotificationPayload(
                        "INVITE_RECEIVED",
                        "New invite",
                        organizerName + " invited you to " + title,
                        eventId,
                        Instant.now()
                );
                messagingTemplate.convertAndSendToUser(invitee.id().toString(), "/queue/notifications", wsPayload);
            }

            if (preference == NotificationPreference.EMAIL || preference == NotificationPreference.BOTH) {
                String emailBody = String.format(
                        "Hi %s,\n\nYou have been invited to a new meeting: '%s'.\nOrganizer: %s\nStart Time: %s (%s)\n\nPlease log in to respond.",
                        invitee.name(), title, organizerName, startTime, timezone
                );
                emailService.send(invitee.email(), "Meeting Invite: " + title, emailBody);
            }
        } catch (Exception e) {
            log.error("Failed to send meeting created notification async to user: {}", invitee.id(), e);
        }
    }

    @Async("notificationTaskExecutor")
    public void sendMeetingCancelledAsync(UserProfileResponse invitee, UUID eventId, String title) {
        log.debug("Sending meeting cancelled notification asynchronously to user: {} on thread: {}", invitee.id(), Thread.currentThread().getName());
        try {
            NotificationPreference preference = invitee.notificationPreference();

            if (preference == NotificationPreference.IN_APP || preference == NotificationPreference.BOTH) {
                NotificationPayload wsPayload = new NotificationPayload(
                        "MEETING_CANCELLED",
                        "Meeting Cancelled",
                        "Meeting has been cancelled: " + title,
                        eventId,
                        Instant.now()
                );
                messagingTemplate.convertAndSendToUser(invitee.id().toString(), "/queue/notifications", wsPayload);
            }

            if (preference == NotificationPreference.EMAIL || preference == NotificationPreference.BOTH) {
                String emailBody = String.format("Hi %s,\n\nThe meeting '%s' has been cancelled.", invitee.name(), title);
                emailService.send(invitee.email(), "Meeting Cancelled: " + title, emailBody);
            }
        } catch (Exception e) {
            log.error("Failed to send meeting cancelled notification async to user: {}", invitee.id(), e);
        }
    }

    @Async("notificationTaskExecutor")
    public void sendMeetingUpdatedAsync(UserProfileResponse invitee, UUID eventId, String title) {
        log.debug("Sending meeting updated notification asynchronously to user: {} on thread: {}", invitee.id(), Thread.currentThread().getName());
        try {
            NotificationPreference preference = invitee.notificationPreference();

            if (preference == NotificationPreference.IN_APP || preference == NotificationPreference.BOTH) {
                NotificationPayload wsPayload = new NotificationPayload(
                        "MEETING_UPDATED",
                        "Meeting Updated",
                        "Meeting details updated: " + title,
                        eventId,
                        Instant.now()
                );
                messagingTemplate.convertAndSendToUser(invitee.id().toString(), "/queue/notifications", wsPayload);
            }

            if (preference == NotificationPreference.EMAIL || preference == NotificationPreference.BOTH) {
                String emailBody = String.format("Hi %s,\n\nThe details for the meeting '%s' have been updated.", invitee.name(), title);
                emailService.send(invitee.email(), "Meeting Updated: " + title, emailBody);
            }
        } catch (Exception e) {
            log.error("Failed to send meeting updated notification async to user: {}", invitee.id(), e);
        }
    }

    @Async("notificationTaskExecutor")
    public void sendRsvpUpdatedAsync(UserProfileResponse organizer, UserProfileResponse invitee, UUID eventId, String status) {
        log.debug("Sending RSVP updated notification asynchronously to organizer: {} on thread: {}", organizer.id(), Thread.currentThread().getName());
        try {
            NotificationPreference preference = organizer.notificationPreference();

            if (preference == NotificationPreference.IN_APP || preference == NotificationPreference.BOTH) {
                NotificationPayload wsPayload = new NotificationPayload(
                        "RSVP_UPDATE",
                        "RSVP Response Received",
                        invitee.name() + " has " + status + " your invite.",
                        eventId,
                        Instant.now()
                );
                messagingTemplate.convertAndSendToUser(organizer.id().toString(), "/queue/notifications", wsPayload);
            }

            if (preference == NotificationPreference.EMAIL || preference == NotificationPreference.BOTH) {
                String emailBody = String.format("Hi %s,\n\n%s has %s your meeting invitation.", organizer.name(), invitee.name(), status);
                emailService.send(organizer.email(), "RSVP Update: " + invitee.name(), emailBody);
            }
        } catch (Exception e) {
            log.error("Failed to send RSVP updated notification async to organizer: {}", organizer.id(), e);
        }
    }

    @Async("notificationTaskExecutor")
    public void sendReminderAsync(UserProfileResponse attendee, UUID eventId, String title) {
        log.debug("Sending reminder notification asynchronously to user: {} on thread: {}", attendee.id(), Thread.currentThread().getName());
        try {
            NotificationPreference preference = attendee.notificationPreference();

            if (preference == NotificationPreference.IN_APP || preference == NotificationPreference.BOTH) {
                NotificationPayload wsPayload = new NotificationPayload(
                        "REMINDER",
                        "Meeting Reminder",
                        "Meeting: " + title + " starts in 15 minutes.",
                        eventId,
                        Instant.now()
                );
                messagingTemplate.convertAndSendToUser(attendee.id().toString(), "/queue/notifications", wsPayload);
            }

            if (preference == NotificationPreference.EMAIL || preference == NotificationPreference.BOTH) {
                String emailBody = String.format("Hi %s,\n\nYour meeting '%s' starts in 15 minutes.", attendee.name(), title);
                emailService.send(attendee.email(), "Meeting Reminder: " + title, emailBody);
            }
        } catch (Exception e) {
            log.error("Failed to send reminder notification async to user: {}", attendee.id(), e);
        }
    }
}
