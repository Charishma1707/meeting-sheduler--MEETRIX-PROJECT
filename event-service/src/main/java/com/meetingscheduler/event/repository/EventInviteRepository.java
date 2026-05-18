package com.meetingscheduler.event.repository;

import com.meetingscheduler.event.entity.Event;
import com.meetingscheduler.event.entity.EventInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventInviteRepository extends JpaRepository<EventInvite, UUID> {

    List<EventInvite> findByEventId(UUID eventId);

    Optional<EventInvite> findByEventIdAndInviteeId(UUID eventId, UUID inviteeId);

    List<EventInvite> findByEventIn(List<Event> events);
}
