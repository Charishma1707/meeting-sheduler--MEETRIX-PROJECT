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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final EventInviteRepository eventInviteRepository;
    private final RecurringRuleRepository recurringRuleRepository;
    private final RecurringRuleExpander recurringRuleExpander;
    private final UserServiceClient userServiceClient;
    private final AvailabilityServiceClient availabilityServiceClient;
    private final KafkaEventPublisher kafkaEventPublisher;
    @Qualifier("eventTaskExecutor")
    private final Executor eventTaskExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public EventResponse createEvent(CreateEventRequest request, UUID organizerId) {
        log.info("Creating event for organizer: {}", organizerId);

        // 1. Parse and validate local time
        ZoneId zoneId = ZoneId.of(request.timezone());
        LocalDateTime startLdt = LocalDateTime.parse(request.startTimeLocal());
        LocalDateTime endLdt = LocalDateTime.parse(request.endTimeLocal());
        Instant startInstant = startLdt.atZone(zoneId).toInstant();
        Instant endInstant = endLdt.atZone(zoneId).toInstant();

        if (!endInstant.isAfter(startInstant)) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        // 2. Validate all users exist
        List<UUID> allUserIds = new ArrayList<>();
        allUserIds.add(organizerId);
        allUserIds.addAll(request.inviteeIds());
        List<UUID> uniqueUserIds = allUserIds.stream().distinct().toList();

        // Execute both Feign calls concurrently
        CompletableFuture<List<UserProfileResponse>> usersFuture = CompletableFuture.supplyAsync(
                () -> userServiceClient.batchGetUsers(new BatchUserRequest(uniqueUserIds)), eventTaskExecutor);

        CompletableFuture<ConflictCheckResponse> conflictFuture = CompletableFuture.supplyAsync(
                () -> availabilityServiceClient.checkConflicts(new ConflictCheckRequest(uniqueUserIds, startInstant, endInstant)), eventTaskExecutor);

        CompletableFuture.allOf(usersFuture, conflictFuture).join();

        List<UserProfileResponse> users = usersFuture.join();
        ConflictCheckResponse conflictResponse = conflictFuture.join();

        if (users.size() < uniqueUserIds.size()) {
            throw new ResourceNotFoundException("One or more user profiles could not be found");
        }

        Map<UUID, String> userNameMap = users.stream()
                .collect(Collectors.toMap(UserProfileResponse::id, UserProfileResponse::name));

        Map<UUID, UserProfileResponse> userProfileMap = users.stream()
                .collect(Collectors.toMap(UserProfileResponse::id, user -> user));
        if (conflictResponse.hasConflict() && !conflictResponse.conflicts().isEmpty()) {
            ConflictDetail first = conflictResponse.conflicts().get(0);
            String name = userNameMap.getOrDefault(first.userId(), first.userId().toString());
            throw new ConflictException("Conflict for: " + name + " at " + first.conflictingEventTitle());
        }

        // 4. Save Event
        Event event = Event.builder()
                .title(request.title())
                .description(request.description())
                .location(request.location())
                .startTime(startInstant)
                .endTime(endInstant)
                .timezone(request.timezone())
                .organizerId(organizerId)
                .status(EventStatus.ACTIVE)
                .build();
        event = eventRepository.save(event);

        // 5. Save EventInvites
        List<EventInvite> invites = new ArrayList<>();
        for (UUID inviteeId : request.inviteeIds()) {
            EventInvite invite = EventInvite.builder()
                    .event(event)
                    .inviteeId(inviteeId)
                    .status(InviteStatus.PENDING)
                    .build();
            invites.add(eventInviteRepository.save(invite));
        }

        // 6. Save RecurringRule
        RecurringRule rule = null;
        if (request.recurrence() != null) {
            rule = RecurringRule.builder()
                    .event(event)
                    .type(request.recurrence().type())
                    .interval(request.recurrence().interval())
                    .until(request.recurrence().until())
                    .exceptions("[]")
                    .build();
            rule = recurringRuleRepository.save(rule);
        }

        // 7. Publish to Kafka
        kafkaEventPublisher.publishMeetingCreated(event, request.inviteeIds());

        return mapToEventResponse(event, invites, rule, zoneId, organizerId, userProfileMap);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(UUID id, UUID requestingUserId) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        List<EventInvite> invites = eventInviteRepository.findByEventId(id);
        RecurringRule rule = recurringRuleRepository.findByEventId(id).orElse(null);

        Set<UUID> userIds = new HashSet<>();
        userIds.add(event.getOrganizerId());
        invites.forEach(invite -> userIds.add(invite.getInviteeId()));

        // Get requesting user's timezone and load user display names concurrently
        CompletableFuture<UserProfileResponse> requestingUserFuture = CompletableFuture.supplyAsync(
                () -> userServiceClient.getMyProfile(requestingUserId.toString()), eventTaskExecutor);

        CompletableFuture<List<UserProfileResponse>> usersFuture = CompletableFuture.supplyAsync(
                () -> userServiceClient.batchGetUsers(new BatchUserRequest(new ArrayList<>(userIds))), eventTaskExecutor);

        CompletableFuture.allOf(requestingUserFuture, usersFuture).join();

        UserProfileResponse requestingUser = requestingUserFuture.join();
        List<UserProfileResponse> users = usersFuture.join();
        ZoneId zoneId = ZoneId.of(requestingUser.timezone());
        Map<UUID, UserProfileResponse> userProfileMap = users.stream()
                .collect(Collectors.toMap(UserProfileResponse::id, user -> user));

        return mapToEventResponse(event, invites, rule, zoneId, requestingUserId, userProfileMap);
    }

    @Transactional
    public EventResponse updateEvent(UUID id, UpdateEventRequest request, UUID requestingUserId) {
        log.info("Updating event: {} by user: {}", id, requestingUserId);

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        if (!event.getOrganizerId().equals(requestingUserId)) {
            throw new ForbiddenException("Only the organizer can update this event");
        }

        // Parse and validate times
        ZoneId zoneId = ZoneId.of(request.timezone());
        LocalDateTime startLdt = LocalDateTime.parse(request.startTimeLocal());
        LocalDateTime endLdt = LocalDateTime.parse(request.endTimeLocal());
        Instant startInstant = startLdt.atZone(zoneId).toInstant();
        Instant endInstant = endLdt.atZone(zoneId).toInstant();

        if (!endInstant.isAfter(startInstant)) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        // Validate users
        List<UUID> allUserIds = new ArrayList<>();
        allUserIds.add(requestingUserId);
        allUserIds.addAll(request.inviteeIds());
        List<UUID> uniqueUserIds = allUserIds.stream().distinct().toList();

        // Execute both Feign calls concurrently
        CompletableFuture<List<UserProfileResponse>> usersFuture = CompletableFuture.supplyAsync(
                () -> userServiceClient.batchGetUsers(new BatchUserRequest(uniqueUserIds)), eventTaskExecutor);

        CompletableFuture<ConflictCheckResponse> conflictFuture = CompletableFuture.supplyAsync(
                () -> availabilityServiceClient.checkConflicts(new ConflictCheckRequest(uniqueUserIds, startInstant, endInstant)), eventTaskExecutor);

        CompletableFuture.allOf(usersFuture, conflictFuture).join();

        List<UserProfileResponse> users = usersFuture.join();
        ConflictCheckResponse conflictResponse = conflictFuture.join();

        if (users.size() < uniqueUserIds.size()) {
            throw new ResourceNotFoundException("One or more user profiles could not be found");
        }

        Map<UUID, String> userNameMap = users.stream()
                .collect(Collectors.toMap(UserProfileResponse::id, UserProfileResponse::name));

        Map<UUID, UserProfileResponse> userProfileMap = users.stream()
                .collect(Collectors.toMap(UserProfileResponse::id, user -> user));
        if (conflictResponse.hasConflict() && !conflictResponse.conflicts().isEmpty()) {
            ConflictDetail conflict = conflictResponse.conflicts().stream()
                    .filter(c -> !c.conflictingEventId().equals(id))
                    .findFirst()
                    .orElse(null);
            if (conflict != null) {
                String name = userNameMap.getOrDefault(conflict.userId(), conflict.userId().toString());
                throw new ConflictException("Conflict for: " + name + " at " + conflict.conflictingEventTitle());
            }
        }

        // Update basic attributes
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setLocation(request.location());
        event.setStartTime(startInstant);
        event.setEndTime(endInstant);
        event.setTimezone(request.timezone());
        event = eventRepository.save(event);

        // Replace invites
        // First delete existing ones
        List<EventInvite> existingInvites = eventInviteRepository.findByEventId(id);
        eventInviteRepository.deleteAll(existingInvites);

        List<EventInvite> newInvites = new ArrayList<>();
        for (UUID inviteeId : request.inviteeIds()) {
            EventInvite invite = EventInvite.builder()
                    .event(event)
                    .inviteeId(inviteeId)
                    .status(InviteStatus.PENDING)
                    .build();
            newInvites.add(eventInviteRepository.save(invite));
        }

        // Replace Recurrence Rule
        recurringRuleRepository.deleteByEventId(id);
        RecurringRule rule = null;
        if (request.recurrence() != null) {
            rule = RecurringRule.builder()
                    .event(event)
                    .type(request.recurrence().type())
                    .interval(request.recurrence().interval())
                    .until(request.recurrence().until())
                    .exceptions("[]")
                    .build();
            rule = recurringRuleRepository.save(rule);
        }

        // Publish to Kafka
        kafkaEventPublisher.publishMeetingUpdated(event, request.inviteeIds());

        return mapToEventResponse(event, newInvites, rule, zoneId, requestingUserId, userProfileMap);
    }

    @Transactional
    public void deleteEvent(UUID id, UUID requestingUserId) {
        log.info("Deleting event: {} by user: {}", id, requestingUserId);

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with ID: " + id));

        if (!event.getOrganizerId().equals(requestingUserId)) {
            throw new ForbiddenException("Only the organizer can cancel this event");
        }

        event.setStatus(EventStatus.CANCELLED);
        eventRepository.save(event);

        List<EventInvite> invites = eventInviteRepository.findByEventId(id);
        List<UUID> inviteeIds = invites.stream().map(EventInvite::getInviteeId).toList();

        kafkaEventPublisher.publishMeetingCancelled(event, inviteeIds);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getMyEvents(UUID userId, Instant from, Instant to, Pageable pageable) {
        log.info("Fetching events for user: {} between {} and {}", userId, from, to);

        Page<Event> eventsPage = eventRepository.findAllForUser(userId, pageable);
        List<Event> events = eventsPage.getContent();

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        // Bulk load invites and rules to avoid N+1 queries
        List<EventInvite> allInvites = eventInviteRepository.findByEventIn(events);
        List<RecurringRule> allRules = recurringRuleRepository.findByEventIn(events);

        Map<UUID, List<EventInvite>> invitesMap = allInvites.stream()
                .collect(Collectors.groupingBy(i -> i.getEvent().getId()));

        Map<UUID, RecurringRule> rulesMap = allRules.stream()
                .collect(Collectors.toMap(r -> r.getEvent().getId(), r -> r));

        Set<UUID> allUserIds = new HashSet<>();
        allUserIds.add(userId);
        events.forEach(event -> allUserIds.add(event.getOrganizerId()));
        allInvites.forEach(invite -> allUserIds.add(invite.getInviteeId()));

        List<UserProfileResponse> users = userServiceClient.batchGetUsers(new BatchUserRequest(new ArrayList<>(allUserIds)));
        Map<UUID, UserProfileResponse> userProfileMap = users.stream()
                .collect(Collectors.toMap(UserProfileResponse::id, user -> user));

        UserProfileResponse requestingUser = userProfileMap.get(userId);
        ZoneId displayZone = ZoneId.of(requestingUser.timezone());

        List<EventResponse> flatList = new ArrayList<>();

        for (Event event : events) {
            List<EventInvite> invites = invitesMap.getOrDefault(event.getId(), Collections.emptyList());
            RecurringRule rule = rulesMap.get(event.getId());

            if (rule == null) {
                // Non-recurring: check overlap with [from, to]
                if (event.getEndTime().isAfter(from) && event.getStartTime().isBefore(to)) {
                    flatList.add(mapToEventResponse(event, invites, null, displayZone, userId, userProfileMap));
                }
            } else {
                // Recurring: expand occurrences
                List<TimeSlot> slots = recurringRuleExpander.expand(rule, from, to);
                for (TimeSlot slot : slots) {
                    Event virtualEvent = Event.builder()
                            .id(event.getId())
                            .title(event.getTitle())
                            .description(event.getDescription())
                            .location(event.getLocation())
                            .startTime(slot.start())
                            .endTime(slot.end())
                            .timezone(event.getTimezone())
                            .organizerId(event.getOrganizerId())
                            .status(event.getStatus())
                            .version(event.getVersion())
                            .createdAt(event.getCreatedAt())
                            .updatedAt(event.getUpdatedAt())
                            .build();
                    flatList.add(mapToEventResponse(virtualEvent, invites, rule, displayZone, userId, userProfileMap));
                }
            }
        }

        return flatList;
    }

    @Transactional
    public EventResponse rsvp(UUID id, UUID inviteeId, InviteStatus status) {
        log.info("RSVP for event: {} by invitee: {} status: {}", id, inviteeId, status);

        if (status == InviteStatus.PENDING) {
            throw new IllegalArgumentException("RSVP status must be ACCEPTED or DECLINED");
        }

        EventInvite invite = eventInviteRepository.findByEventIdAndInviteeId(id, inviteeId)
                .orElseThrow(() -> new ResourceNotFoundException("No invitation found for event ID: " + id + " and invitee ID: " + inviteeId));

        invite.setStatus(status);
        invite.setRespondedAt(Instant.now());
        invite = eventInviteRepository.save(invite);

        Event event = invite.getEvent();
        List<EventInvite> allInvites = eventInviteRepository.findByEventId(id);
        RecurringRule rule = recurringRuleRepository.findByEventId(id).orElse(null);

        kafkaEventPublisher.publishRsvpUpdated(invite, event);

        ZoneId displayZone = ZoneId.of(event.getTimezone());

        Set<UUID> allUserIds = new HashSet<>();
        allUserIds.add(event.getOrganizerId());
        allInvites.forEach(i -> allUserIds.add(i.getInviteeId()));

        List<UserProfileResponse> users = userServiceClient.batchGetUsers(new BatchUserRequest(new ArrayList<>(allUserIds)));
        Map<UUID, UserProfileResponse> userProfileMap = users.stream()
                .collect(Collectors.toMap(UserProfileResponse::id, user -> user));

        return mapToEventResponse(event, allInvites, rule, displayZone, inviteeId, userProfileMap);
    }

    @Transactional(readOnly = true)
    public Map<String, List<TimeSlot>> getBulkBusySlots(BulkBusySlotsRequest request) {
        log.info("Resolving bulk busy slots for users: {}", request.userIds());

        List<Event> activeEvents = eventRepository.findAllActiveForUsers(request.userIds());
        if (activeEvents.isEmpty()) {
            return request.userIds().stream().collect(Collectors.toMap(UUID::toString, u -> new ArrayList<>()));
        }

        List<EventInvite> allInvites = eventInviteRepository.findByEventIn(activeEvents);
        List<RecurringRule> allRules = recurringRuleRepository.findByEventIn(activeEvents);

        Map<UUID, List<EventInvite>> invitesMap = allInvites.stream()
                .collect(Collectors.groupingBy(i -> i.getEvent().getId()));

        Map<UUID, RecurringRule> rulesMap = allRules.stream()
                .collect(Collectors.toMap(r -> r.getEvent().getId(), r -> r));

        Map<String, List<TimeSlot>> userBusySlots = new HashMap<>();
        for (UUID userId : request.userIds()) {
            userBusySlots.put(userId.toString(), new ArrayList<>());
        }

        for (Event event : activeEvents) {
            List<EventInvite> invites = invitesMap.getOrDefault(event.getId(), Collections.emptyList());
            RecurringRule rule = rulesMap.get(event.getId());

            // Identify who is blocked by this event
            Set<UUID> blockedUsers = new HashSet<>();
            blockedUsers.add(event.getOrganizerId());

            for (EventInvite invite : invites) {
                if (invite.getStatus() == InviteStatus.ACCEPTED) {
                    blockedUsers.add(invite.getInviteeId());
                }
            }

            // Exclude users not requested in BulkBusySlotsRequest
            blockedUsers.retainAll(request.userIds());

            if (blockedUsers.isEmpty()) {
                continue;
            }

            // Obtain slots
            List<TimeSlot> slots = new ArrayList<>();
            if (rule == null) {
                if (event.getEndTime().isAfter(request.from()) && event.getStartTime().isBefore(request.to())) {
                    slots.add(new TimeSlot(event.getStartTime(), event.getEndTime()));
                }
            } else {
                slots.addAll(recurringRuleExpander.expand(rule, request.from(), request.to()));
            }

            for (UUID userId : blockedUsers) {
                userBusySlots.get(userId.toString()).addAll(slots);
            }
        }

        // Sort time slots for each user for cleaner visualization
        for (List<TimeSlot> list : userBusySlots.values()) {
            list.sort(Comparator.comparing(TimeSlot::start));
        }

        return userBusySlots;
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getUpcomingEvents(int withinMinutes) {
        log.info("Fetching upcoming events within {} minutes", withinMinutes);

        Instant now = Instant.now();
        Instant endThreshold = now.plus(withinMinutes, ChronoUnit.MINUTES);

        List<Event> upcomingEvents = eventRepository.findUpcomingActiveEvents(now, endThreshold);
        if (upcomingEvents.isEmpty()) {
            return Collections.emptyList();
        }

        List<EventInvite> allInvites = eventInviteRepository.findByEventIn(upcomingEvents);
        List<RecurringRule> allRules = recurringRuleRepository.findByEventIn(upcomingEvents);

        Map<UUID, List<EventInvite>> invitesMap = allInvites.stream()
                .collect(Collectors.groupingBy(i -> i.getEvent().getId()));

        Map<UUID, RecurringRule> rulesMap = allRules.stream()
                .collect(Collectors.toMap(r -> r.getEvent().getId(), r -> r));

        return upcomingEvents.stream()
                .map(event -> {
                    List<EventInvite> invites = invitesMap.getOrDefault(event.getId(), Collections.emptyList());
                    RecurringRule rule = rulesMap.get(event.getId());
                    ZoneId zoneId = ZoneId.of(event.getTimezone()); // Organizer's timezone
                    return mapToEventResponse(event, invites, rule, zoneId, null, Collections.emptyMap());
                })
                .toList();
    }

    private EventResponse mapToEventResponse(Event event, List<EventInvite> invites, RecurringRule rule, ZoneId displayZone, UUID requestingUserId, Map<UUID, UserProfileResponse> userProfileMap) {
        List<InviteResponse> inviteResponses = invites.stream()
                .map(i -> {
                    UserProfileResponse profile = userProfileMap.get(i.getInviteeId());
                    return new InviteResponse(
                            i.getId(),
                            i.getInviteeId(),
                            profile != null ? profile.name() : null,
                            profile != null ? profile.email() : null,
                            i.getStatus(),
                            i.getRespondedAt()
                    );
                })
                .toList();

        RecurrenceResponse recurrenceResponse = null;
        if (rule != null) {
            recurrenceResponse = new RecurrenceResponse(
                    rule.getType(),
                    rule.getInterval(),
                    rule.getUntil(),
                    parseExceptionsToList(rule.getExceptions())
            );
        }

        ZonedDateTime localStart = event.getStartTime().atZone(displayZone);
        ZonedDateTime localEnd = event.getEndTime().atZone(displayZone);

        String organizerName = null;
        String organizerEmail = null;
        if (userProfileMap != null) {
            UserProfileResponse organizerProfile = userProfileMap.get(event.getOrganizerId());
            if (organizerProfile != null) {
                organizerName = organizerProfile.name();
                organizerEmail = organizerProfile.email();
            }
        }

        String myRsvpStatus = null;
        if (requestingUserId != null) {
            if (event.getOrganizerId().equals(requestingUserId)) {
                myRsvpStatus = InviteStatus.ORGANIZER.name();
            } else {
                InviteResponse currentInvite = inviteResponses.stream()
                        .filter(inv -> inv.inviteeId().equals(requestingUserId))
                        .findFirst()
                        .orElse(null);
                myRsvpStatus = currentInvite != null ? currentInvite.status().name() : null;
            }
        }

        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getLocation(),
                event.getStartTime(),
                event.getEndTime(),
                localStart.toLocalDateTime().toString(),
                localEnd.toLocalDateTime().toString(),
                event.getTimezone(),
                event.getOrganizerId(),
                organizerName,
                organizerEmail,
                event.getStatus(),
                inviteResponses,
                myRsvpStatus,
                recurrenceResponse
        );
    }

    private List<LocalDate> parseExceptionsToList(String exceptionsJson) {
        if (exceptionsJson == null || exceptionsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> list = objectMapper.readValue(exceptionsJson, new TypeReference<List<String>>() {});
            return list.stream().map(LocalDate::parse).toList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
