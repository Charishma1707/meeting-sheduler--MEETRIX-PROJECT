package com.meetingscheduler.notification.client;

import com.meetingscheduler.notification.dto.BatchUserRequest;
import com.meetingscheduler.notification.dto.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @PostMapping("/api/users/internal/batch")
    List<UserProfileResponse> getProfilesBatch(@RequestBody BatchUserRequest request);
}
