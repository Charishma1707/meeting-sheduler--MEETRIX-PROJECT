package com.meetingscheduler.user.repository;

import com.meetingscheduler.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    List<UserProfile> findByNameContainingIgnoreCase(String name);
    List<UserProfile> findAllByIdIn(List<UUID> ids);
}
