package com.meetingscheduler.gateway.config;

import com.meetingscheduler.gateway.filter.JwtAuthFilter;
import com.meetingscheduler.gateway.filter.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter();
    }

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter();
    }
}
