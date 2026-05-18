package com.meetingscheduler.availability.client;

import com.meetingscheduler.availability.dto.BulkBusySlotsRequest;
import com.meetingscheduler.availability.dto.TimeSlot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "event-service")
public interface EventServiceClient {

    @PostMapping("/api/events/internal/bulk-busy-slots")
    Map<String, List<TimeSlot>> getBulkBusySlots(@RequestBody BulkBusySlotsRequest request);
}
