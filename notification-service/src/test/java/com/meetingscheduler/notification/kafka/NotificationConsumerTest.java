package com.meetingscheduler.notification.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingscheduler.notification.client.UserServiceClient;
import com.meetingscheduler.notification.consumer.NotificationConsumer;
import com.meetingscheduler.notification.dto.BatchUserRequest;
import com.meetingscheduler.notification.dto.NotificationPreference;
import com.meetingscheduler.notification.dto.UserProfileResponse;
import com.meetingscheduler.notification.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationConsumerTest {

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private EmailService emailService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    @Test
    @SuppressWarnings("unchecked")
    public void consumeMeetingCreated_inAppUser_sendsWebSocketNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();

        Map<String, Object> mockPayload = new HashMap<>();
        mockPayload.put("eventId", eventId.toString());
        mockPayload.put("title", "Design Review");
        mockPayload.put("organizerId", organizerId.toString());
        mockPayload.put("inviteeIds", List.of(inviteeId.toString()));
        mockPayload.put("startTime", "2025-06-02T10:00:00Z");
        mockPayload.put("timezone", "Asia/Kolkata");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(mockPayload);

        UserProfileResponse organizer = new UserProfileResponse(
                organizerId, "John Doe", "john@example.com", "Asia/Kolkata", NotificationPreference.BOTH
        );
        UserProfileResponse invitee = new UserProfileResponse(
                inviteeId, "Alice Smith", "alice@example.com", "Asia/Kolkata", NotificationPreference.IN_APP
        );

        when(userServiceClient.getProfilesBatch(any(BatchUserRequest.class))).thenReturn(List.of(organizer, invitee));

        notificationConsumer.consumeMeetingCreated("dummy-json");

        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(inviteeId.toString()), eq("/queue/notifications"), any());
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void consumeMeetingCreated_emailUser_sendsEmail() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();

        Map<String, Object> mockPayload = new HashMap<>();
        mockPayload.put("eventId", eventId.toString());
        mockPayload.put("title", "Design Review");
        mockPayload.put("organizerId", organizerId.toString());
        mockPayload.put("inviteeIds", List.of(inviteeId.toString()));
        mockPayload.put("startTime", "2025-06-02T10:00:00Z");
        mockPayload.put("timezone", "Asia/Kolkata");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(mockPayload);

        UserProfileResponse organizer = new UserProfileResponse(
                organizerId, "John Doe", "john@example.com", "Asia/Kolkata", NotificationPreference.BOTH
        );
        UserProfileResponse invitee = new UserProfileResponse(
                inviteeId, "Alice Smith", "alice@example.com", "Asia/Kolkata", NotificationPreference.EMAIL
        );

        when(userServiceClient.getProfilesBatch(any(BatchUserRequest.class))).thenReturn(List.of(organizer, invitee));

        notificationConsumer.consumeMeetingCreated("dummy-json");

        verify(emailService, times(1)).send(eq("alice@example.com"), contains("Design Review"), anyString());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void consumeMeetingCreated_bothUser_sendsBothWebSocketAndEmail() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();

        Map<String, Object> mockPayload = new HashMap<>();
        mockPayload.put("eventId", eventId.toString());
        mockPayload.put("title", "Design Review");
        mockPayload.put("organizerId", organizerId.toString());
        mockPayload.put("inviteeIds", List.of(inviteeId.toString()));
        mockPayload.put("startTime", "2025-06-02T10:00:00Z");
        mockPayload.put("timezone", "Asia/Kolkata");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(mockPayload);

        UserProfileResponse organizer = new UserProfileResponse(
                organizerId, "John Doe", "john@example.com", "Asia/Kolkata", NotificationPreference.BOTH
        );
        UserProfileResponse invitee = new UserProfileResponse(
                inviteeId, "Alice Smith", "alice@example.com", "Asia/Kolkata", NotificationPreference.BOTH
        );

        when(userServiceClient.getProfilesBatch(any(BatchUserRequest.class))).thenReturn(List.of(organizer, invitee));

        notificationConsumer.consumeMeetingCreated("dummy-json");

        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(inviteeId.toString()), eq("/queue/notifications"), any());
        verify(emailService, times(1)).send(eq("alice@example.com"), contains("Design Review"), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void consumeMeetingCancelled_notifiesAllInvitees() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID invitee1 = UUID.randomUUID();
        UUID invitee2 = UUID.randomUUID();

        Map<String, Object> mockPayload = new HashMap<>();
        mockPayload.put("eventId", eventId.toString());
        mockPayload.put("title", "Scrum");
        mockPayload.put("inviteeIds", List.of(invitee1.toString(), invitee2.toString()));

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(mockPayload);

        UserProfileResponse u1 = new UserProfileResponse(invitee1, "U1", "u1@ex.com", "UTC", NotificationPreference.IN_APP);
        UserProfileResponse u2 = new UserProfileResponse(invitee2, "U2", "u2@ex.com", "UTC", NotificationPreference.IN_APP);

        when(userServiceClient.getProfilesBatch(any(BatchUserRequest.class))).thenReturn(List.of(u1, u2));

        notificationConsumer.consumeMeetingCancelled("dummy-json");

        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(invitee1.toString()), eq("/queue/notifications"), any());
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(invitee2.toString()), eq("/queue/notifications"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void consumeRsvpUpdated_notifiesOrganizer() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        Map<String, Object> mockPayload = new HashMap<>();
        mockPayload.put("eventId", eventId.toString());
        mockPayload.put("inviteeId", inviteeId.toString());
        mockPayload.put("organizerId", organizerId.toString());
        mockPayload.put("status", "ACCEPTED");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(mockPayload);

        UserProfileResponse invitee = new UserProfileResponse(inviteeId, "Invitee", "i@ex.com", "UTC", NotificationPreference.IN_APP);
        UserProfileResponse organizer = new UserProfileResponse(organizerId, "Organizer", "o@ex.com", "UTC", NotificationPreference.IN_APP);

        when(userServiceClient.getProfilesBatch(any(BatchUserRequest.class))).thenReturn(List.of(invitee, organizer));

        notificationConsumer.consumeRsvpUpdated("dummy-json");

        verify(messagingTemplate, times(1)).convertAndSendToUser(eq(organizerId.toString()), eq("/queue/notifications"), any());
    }
}
