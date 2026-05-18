package com.meetingscheduler.audit.repository;

import com.meetingscheduler.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEventIdOrderByTimestampAsc(UUID eventId);

    Page<AuditLog> findByActorUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);

    Page<AuditLog> findByTimestampBetweenAndActionContaining(Instant from, Instant to, String action, Pageable pageable);
}
