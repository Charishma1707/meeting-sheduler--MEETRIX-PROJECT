package com.meetingscheduler.eureka.controller;

import com.netflix.discovery.EurekaClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class StatusController {

    private final EurekaClient eurekaClient;

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        int serviceCount = eurekaClient.getApplications().size();
        return Map.of(
                "status", "UP",
                "registeredServices", serviceCount
        );
    }
}
