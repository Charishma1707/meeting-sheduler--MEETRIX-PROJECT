package com.meetingscheduler.event.repository;

import com.meetingscheduler.event.entity.Event;
import com.meetingscheduler.event.entity.RecurringRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringRuleRepository extends JpaRepository<RecurringRule, UUID> {

    Optional<RecurringRule> findByEventId(UUID eventId);

    List<RecurringRule> findByEventIn(List<Event> events);

    void deleteByEventId(UUID eventId);
}
