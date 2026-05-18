package com.meetingscheduler.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    /**
     * Simulates sending a meeting notification email by logging the details.
     *
     * @param to      recipient email address
     * @param subject email subject line
     * @param body    email content body
     */
    public void send(String to, String subject, String body) {
        log.info("[EMAIL] To: {} | Subject: {} | Body: {}", to, subject, body);
    }
}
