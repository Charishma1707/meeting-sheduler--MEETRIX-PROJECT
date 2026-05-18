package com.meetingscheduler.reminder.scheduler;

import com.meetingscheduler.reminder.client.EventServiceClient;
import com.meetingscheduler.reminder.dto.UpcomingEventDto;
import com.meetingscheduler.reminder.kafka.ReminderKafkaPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReminderSchedulerTest {

    @Mock
    private EventServiceClient eventServiceClient;

    @Mock
    private ReminderKafkaPublisher kafkaPublisher;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private ReminderScheduler reminderScheduler;

    @BeforeEach
    public void setup() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    public void checkUpcomingMeetings_noEvents_doesNotPublish() {
        when(eventServiceClient.getUpcomingEvents(15)).thenReturn(List.of());

        reminderScheduler.checkUpcomingMeetings();

        verify(kafkaPublisher, never()).publishReminder(any());
        verify(kafkaPublisher, never()).publishAudit(any());
    }

    @Test
    public void checkUpcomingMeetings_newEvent_publishesReminderAndSetsRedisKey() {
        UUID eventId = UUID.randomUUID();
        Instant startTime = Instant.parse("2025-06-02T10:00:00Z");
        UpcomingEventDto mockEvent = new UpcomingEventDto(
                eventId, "Sync Up", UUID.randomUUID(), List.of(UUID.randomUUID()), startTime
        );

        when(eventServiceClient.getUpcomingEvents(15)).thenReturn(List.of(mockEvent));
        when(valueOps.get(anyString())).thenReturn(null);

        reminderScheduler.checkUpcomingMeetings();

        verify(kafkaPublisher, times(1)).publishReminder(mockEvent);
        verify(kafkaPublisher, times(1)).publishAudit(mockEvent);
        verify(valueOps, times(1)).set(
                contains(eventId.toString()),
                eq("true"),
                any()
        );
    }

    @Test
    public void checkUpcomingMeetings_alreadySentReminder_skipsPublishing() {
        UUID eventId = UUID.randomUUID();
        Instant startTime = Instant.parse("2025-06-02T10:00:00Z");
        UpcomingEventDto mockEvent = new UpcomingEventDto(
                eventId, "Sync Up", UUID.randomUUID(), List.of(UUID.randomUUID()), startTime
        );

        when(eventServiceClient.getUpcomingEvents(15)).thenReturn(List.of(mockEvent));
        when(valueOps.get(anyString())).thenReturn("true");

        reminderScheduler.checkUpcomingMeetings();

        verify(kafkaPublisher, never()).publishReminder(any());
        verify(kafkaPublisher, never()).publishAudit(any());
    }

    @Test
    public void checkUpcomingMeetings_multipleEvents_publishesForEach() {
        UUID e1 = UUID.randomUUID();
        UUID e2 = UUID.randomUUID();
        UpcomingEventDto mockEvent1 = new UpcomingEventDto(
                e1, "Sync Up", UUID.randomUUID(), List.of(UUID.randomUUID()), Instant.parse("2025-06-02T10:00:00Z")
        );
        UpcomingEventDto mockEvent2 = new UpcomingEventDto(
                e2, "Sprint Retro", UUID.randomUUID(), List.of(UUID.randomUUID()), Instant.parse("2025-06-02T10:15:00Z")
        );

        when(eventServiceClient.getUpcomingEvents(15)).thenReturn(List.of(mockEvent1, mockEvent2));
        when(valueOps.get(anyString())).thenReturn(null);

        reminderScheduler.checkUpcomingMeetings();

        verify(kafkaPublisher, times(1)).publishReminder(mockEvent1);
        verify(kafkaPublisher, times(1)).publishReminder(mockEvent2);
        verify(kafkaPublisher, times(2)).publishAudit(any());
    }
}
