package com.meetingscheduler.reminder.scheduler;

import com.meetingscheduler.reminder.client.EventServiceClient;
import com.meetingscheduler.reminder.dto.UpcomingEventDto;
import com.meetingscheduler.reminder.kafka.ReminderKafkaPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final EventServiceClient eventServiceClient;
    private final ReminderKafkaPublisher kafkaPublisher;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 60000)
    public void checkUpcomingMeetings() {
        log.info("Starting upcoming meetings reminder check...");
        try {
            // 1. Call EventServiceClient.getUpcomingEvents(withinMinutes=15)
            List<UpcomingEventDto> upcomingEvents = eventServiceClient.getUpcomingEvents(15);
            int sentCount = 0;

            if (upcomingEvents != null) {
                for (UpcomingEventDto event : upcomingEvents) {
                    // a. Build dedup key: "reminder-sent:{eventId}:{startTime.toEpochSecond()}"
                    String dedupKey = String.format("reminder-sent:%s:%d", event.eventId(), event.startTime().getEpochSecond());

                    // b. Check Redis
                    String alreadySent = redisTemplate.opsForValue().get(dedupKey);
                    if (alreadySent != null) {
                        log.debug("Reminder already sent for event: {} starting at {}. Skipping.", event.eventId(), event.startTime());
                        continue;
                    }

                    // c. Publish to Kafka topic reminder.trigger
                    kafkaPublisher.publishReminder(event);

                    // d. Publish to Kafka topic audit.log
                    kafkaPublisher.publishAudit(event);

                    // e. Set Redis key with 20 minutes TTL (1200 seconds)
                    redisTemplate.opsForValue().set(dedupKey, "true", Duration.ofMinutes(20));

                    sentCount++;
                }
            }

            // 3. Log at INFO
            log.info("Sent reminders for {} upcoming meetings", sentCount);
        } catch (Exception e) {
            log.error("Error occurred while checking upcoming meetings and sending reminders", e);
        }
    }
}
