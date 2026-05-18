package com.meetingscheduler.event.repository;

import com.meetingscheduler.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    @Query("SELECT DISTINCT e FROM Event e LEFT JOIN EventInvite i ON i.event = e " +
           "WHERE (e.organizerId = :userId OR i.inviteeId = :userId) " +
           "AND e.status = 'ACTIVE'")
    Page<Event> findAllForUser(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT DISTINCT e FROM Event e LEFT JOIN EventInvite i ON i.event = e " +
           "WHERE (e.organizerId IN :userIds OR (i.inviteeId IN :userIds AND i.status = 'ACCEPTED')) " +
           "AND e.status = 'ACTIVE'")
    List<Event> findAllActiveForUsers(@Param("userIds") List<UUID> userIds);

    @Query("SELECT e FROM Event e WHERE e.status = 'ACTIVE' " +
           "AND e.startTime >= :now AND e.startTime <= :endThreshold")
    List<Event> findUpcomingActiveEvents(@Param("now") Instant now, @Param("endThreshold") Instant endThreshold);
}
