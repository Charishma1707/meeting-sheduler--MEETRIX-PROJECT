package com.meetingscheduler.reminder.client;

import com.meetingscheduler.reminder.dto.EventResponseDto;
import com.meetingscheduler.reminder.dto.InviteResponseDto;
import com.meetingscheduler.reminder.dto.UpcomingEventDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "event-service")
public interface EventServiceClient {

    @GetMapping("/api/events/internal/upcoming")
    List<EventResponseDto> getUpcomingEventsRaw(@RequestParam("withinMinutes") int withinMinutes);

    default List<UpcomingEventDto> getUpcomingEvents(int withinMinutes) {
        List<EventResponseDto> rawList = getUpcomingEventsRaw(withinMinutes);
        if (rawList == null) {
            return List.of();
        }
        return rawList.stream()
                .map(raw -> new UpcomingEventDto(
                        raw.id(),
                        raw.title(),
                        raw.organizerId(),
                        raw.invites() != null ? raw.invites().stream().map(InviteResponseDto::inviteeId).toList() : List.of(),
                        raw.startTime()
                ))
                .toList();
    }
}
