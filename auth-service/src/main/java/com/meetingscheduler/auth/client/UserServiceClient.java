package com.meetingscheduler.auth.client;

import com.meetingscheduler.auth.dto.request.CreateProfileRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service")
public interface UserServiceClient {
    @PostMapping("/api/users/internal/create")
    void createProfile(@RequestBody CreateProfileRequest request);
}
