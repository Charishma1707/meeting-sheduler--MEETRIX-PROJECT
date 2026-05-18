package com.meetingscheduler.event.client;

import com.meetingscheduler.event.dto.BatchUserRequest;
import com.meetingscheduler.event.dto.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @PostMapping("/api/users/internal/batch")
    List<UserProfileResponse> batchGetUsers(@RequestBody BatchUserRequest request);

    @GetMapping("/api/users/me")
    UserProfileResponse getMyProfile(@RequestHeader("X-User-Id") String userId);
}
