package com.meetingscheduler.event.service;

import com.meetingscheduler.event.client.AvailabilityServiceClient;
import com.meetingscheduler.event.client.UserServiceClient;
import com.meetingscheduler.event.dto.*;
import com.meetingscheduler.event.entity.*;
import com.meetingscheduler.event.exception.ConflictException;
import com.meetingscheduler.event.exception.ForbiddenException;
import com.meetingscheduler.event.exception.ResourceNotFoundException;
import com.meetingscheduler.event.kafka.KafkaEventPublisher;
import com.meetingscheduler.event.repository.EventInviteRepository;
import com.meetingscheduler.event.repository.EventRepository;
import com.meetingscheduler.event.repository.RecurringRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventInviteRepository eventInviteRepository;

    @Mock
    private RecurringRuleRepository recurringRuleRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private AvailabilityServiceClient availabilityServiceClient;

    @Mock
    private KafkaEventPublisher kafkaEventPublisher;

    @Mock
    private RecurringRuleExpander recurringRuleExpander;

    @InjectMocks
    private EventService eventService;

    @Test
    public void createEvent_noConflict_savesEventAndInvitesAndPublishesToKafka() {
        UUID organizerId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        CreateEventRequest request = new CreateEventRequest(
                "Team Sync",
                "Weekly Status",
                "Room A",
                "2025-06-02T10:00:00",
                "2025-06-02T11:00:00",
                "Asia/Kolkata",
                List.of(inviteeId),
                null
        );

        List<UserProfileResponse> mockUsers = List.of(
                new UserProfileResponse(organizerId, "Alice Organizer", "alice@test.com", "Asia/Kolkata", "BOTH"),
                new UserProfileResponse(inviteeId, "Bob Invitee", "bob@test.com", "Asia/Kolkata", "BOTH")
        );
        when(userServiceClient.batchGetUsers(any())).thenReturn(mockUsers);
        when(availabilityServiceClient.checkConflicts(any()))
                .thenReturn(new ConflictCheckResponse(false, Collections.emptyList()));

        Event savedEvent = Event.builder()
                .id(UUID.randomUUID())
                .title(request.title())
                .startTime(Instant.parse("2025-06-02T04:30:00Z"))
                .endTime(Instant.parse("2025-06-02T05:30:00Z"))
                .timezone(request.timezone())
                .organizerId(organizerId)
                .status(EventStatus.ACTIVE)
                .build();
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

        EventInvite mockInvite = EventInvite.builder()
                .id(UUID.randomUUID())
                .event(savedEvent)
                .inviteeId(inviteeId)
                .status(InviteStatus.PENDING)
                .build();
        when(eventInviteRepository.save(any(EventInvite.class))).thenReturn(mockInvite);

        EventResponse response = eventService.createEvent(request, organizerId);

        assertNotNull(response);
        assertEquals("Team Sync", response.title());
        assertEquals(EventStatus.ACTIVE, response.status());
        verify(eventRepository, times(1)).save(any(Event.class));
        verify(eventInviteRepository, times(1)).save(any(EventInvite.class));
        verify(kafkaEventPublisher, times(1)).publishMeetingCreated(eq(savedEvent), anyList());
    }

    @Test
    public void createEvent_conflictDetected_throwsConflictException() {
        UUID organizerId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        CreateEventRequest request = new CreateEventRequest(
                "Team Sync",
                "Weekly Status",
                "Room A",
                "2025-06-02T10:00:00",
                "2025-06-02T11:00:00",
                "Asia/Kolkata",
                List.of(inviteeId),
                null
        );

        List<UserProfileResponse> mockUsers = List.of(
                new UserProfileResponse(organizerId, "Alice Organizer", "alice@test.com", "Asia/Kolkata", "BOTH"),
                new UserProfileResponse(inviteeId, "Bob Invitee", "bob@test.com", "Asia/Kolkata", "BOTH")
        );
        when(userServiceClient.batchGetUsers(any())).thenReturn(mockUsers);

        ConflictDetail conflict = new ConflictDetail(inviteeId, UUID.randomUUID(), "Conflicting Meeting", Instant.now(), Instant.now());
        when(availabilityServiceClient.checkConflicts(any()))
                .thenReturn(new ConflictCheckResponse(true, List.of(conflict)));

        assertThrows(ConflictException.class, () -> eventService.createEvent(request, organizerId));

        verify(eventRepository, never()).save(any(Event.class));
        verify(kafkaEventPublisher, never()).publishMeetingCreated(any(), any());
    }

    @Test
    public void createEvent_endTimeBeforeStartTime_throwsValidationException() {
        UUID organizerId = UUID.randomUUID();
        CreateEventRequest request = new CreateEventRequest(
                "Team Sync",
                "Weekly Status",
                "Room A",
                "2025-06-02T11:00:00",
                "2025-06-02T10:00:00", // End before start
                "Asia/Kolkata",
                Collections.emptyList(),
                null
        );

        assertThrows(IllegalArgumentException.class, () -> eventService.createEvent(request, organizerId));

        verify(availabilityServiceClient, never()).checkConflicts(any());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    public void createEvent_withRecurringRule_savesRecurringRule() {
        UUID organizerId = UUID.randomUUID();
        CreateEventRequest request = new CreateEventRequest(
                "Team Sync",
                "Weekly Status",
                "Room A",
                "2025-06-02T10:00:00",
                "2025-06-02T11:00:00",
                "Asia/Kolkata",
                Collections.emptyList(),
                new RecurrenceRequest(RecurrenceType.DAILY, 1, LocalDate.of(2025, 6, 5))
        );

        List<UserProfileResponse> mockUsers = List.of(
                new UserProfileResponse(organizerId, "Alice Organizer", "alice@test.com", "Asia/Kolkata", "BOTH")
        );
        when(userServiceClient.batchGetUsers(any())).thenReturn(mockUsers);
        when(availabilityServiceClient.checkConflicts(any()))
                .thenReturn(new ConflictCheckResponse(false, Collections.emptyList()));

        Event savedEvent = Event.builder()
                .id(UUID.randomUUID())
                .title(request.title())
                .startTime(Instant.parse("2025-06-02T04:30:00Z"))
                .endTime(Instant.parse("2025-06-02T05:30:00Z"))
                .timezone(request.timezone())
                .organizerId(organizerId)
                .status(EventStatus.ACTIVE)
                .build();
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

        RecurringRule rule = RecurringRule.builder()
                .id(UUID.randomUUID())
                .event(savedEvent)
                .type(RecurrenceType.DAILY)
                .interval(1)
                .until(LocalDate.of(2025, 6, 5))
                .build();
        when(recurringRuleRepository.save(any(RecurringRule.class))).thenReturn(rule);

        EventResponse response = eventService.createEvent(request, organizerId);

        assertNotNull(response);
        assertNotNull(response.recurrence());
        assertEquals(RecurrenceType.DAILY, response.recurrence().type());
        verify(recurringRuleRepository, times(1)).save(any(RecurringRule.class));
    }

    @Test
    public void cancelEvent_byOrganizer_setsStatusCancelledAndPublishes() {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        Event event = Event.builder()
                .id(eventId)
                .title("Monthly Review")
                .organizerId(organizerId)
                .status(EventStatus.ACTIVE)
                .build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventInviteRepository.findByEventId(eventId)).thenReturn(Collections.emptyList());
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        eventService.deleteEvent(eventId, organizerId);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertEquals(EventStatus.CANCELLED, eventCaptor.getValue().getStatus(), "Event status should be set to CANCELLED");
        verify(kafkaEventPublisher, times(1)).publishMeetingCancelled(eq(event), anyList());
    }

    @Test
    public void cancelEvent_byNonOrganizer_throwsException() {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        UUID hackerId = UUID.randomUUID();
        Event event = Event.builder()
                .id(eventId)
                .organizerId(organizerId)
                .status(EventStatus.ACTIVE)
                .build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThrows(ForbiddenException.class, () -> eventService.deleteEvent(eventId, hackerId));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    public void updateRsvp_pendingInvite_updatesStatusAndRespondedAt() {
        UUID eventId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();

        Event event = Event.builder()
                .id(eventId)
                .title("Design Critique")
                .timezone("Asia/Kolkata")
                .startTime(Instant.parse("2025-06-02T04:30:00Z"))
                .endTime(Instant.parse("2025-06-02T05:00:00Z"))
                .build();

        EventInvite invite = EventInvite.builder()
                .id(UUID.randomUUID())
                .event(event)
                .inviteeId(inviteeId)
                .status(InviteStatus.PENDING)
                .build();

        when(eventInviteRepository.findByEventIdAndInviteeId(eventId, inviteeId)).thenReturn(Optional.of(invite));
        when(eventInviteRepository.save(any(EventInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventInviteRepository.findByEventId(eventId)).thenReturn(List.of(invite));

        EventResponse response = eventService.rsvp(eventId, inviteeId, InviteStatus.ACCEPTED);

        assertNotNull(response);
        ArgumentCaptor<EventInvite> inviteCaptor = ArgumentCaptor.forClass(EventInvite.class);
        verify(eventInviteRepository).save(inviteCaptor.capture());

        EventInvite savedInvite = inviteCaptor.getValue();
        assertEquals(InviteStatus.ACCEPTED, savedInvite.getStatus());
        assertNotNull(savedInvite.getRespondedAt());
        verify(kafkaEventPublisher, times(1)).publishRsvpUpdated(eq(invite), eq(event));
    }

    @Test
    public void updateRsvp_inviteNotFound_throwsResourceNotFoundException() {
        UUID eventId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();

        when(eventInviteRepository.findByEventIdAndInviteeId(eventId, inviteeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> eventService.rsvp(eventId, inviteeId, InviteStatus.ACCEPTED));
        verify(eventInviteRepository, never()).save(any(EventInvite.class));
    }
}
