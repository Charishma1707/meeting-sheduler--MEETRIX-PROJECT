package com.meetingscheduler.audit.consumer;

import com.meetingscheduler.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditConsumer {

    private final AuditService auditService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_DELAY_VALUE
    )
    @KafkaListener(topics = "audit.log", groupId = "audit-group")
    public void processAuditMessage(String message) {
        log.info("Received message on audit.log topic: {}", message);
        auditService.processAuditMessage(message);
    }

    @DltHandler
    public void handleDlt(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Audit log message failed all attempts on topic {} and entered DLT. Message: {}", topic, message);
    }
}
