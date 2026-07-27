package com.meetingscheduler.notification.security;

import java.security.Principal;

public record UserPrincipal(String userId) implements Principal {
    @Override
    public String getName() {
        return userId;
    }
}
