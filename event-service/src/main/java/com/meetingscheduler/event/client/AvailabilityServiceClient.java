package com.meetingscheduler.event.client;

import com.meetingscheduler.event.dto.ConflictCheckRequest;
import com.meetingscheduler.event.dto.ConflictCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "availability-service")
public interface AvailabilityServiceClient {

    @PostMapping("/api/availability/internal/check-conflicts")
    ConflictCheckResponse checkConflicts(@RequestBody ConflictCheckRequest request);
}
