package com.meetingscheduler.notification.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;

@Component
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] decodedKey = Base64.getDecoder().decode(jwtSecret.trim());
        this.key = Keys.hmacShaKeyFor(decodedKey);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            log.info("Processing WebSocket CONNECT frame with Authorization header present: {}", authHeader != null);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Missing or invalid Authorization header in WebSocket CONNECT frame");
                throw new MessageDeliveryException("Unauthorized: Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId = claims.getSubject();
                if (userId == null) {
                    log.warn("JWT token subject is null");
                    throw new MessageDeliveryException("Unauthorized: Invalid token subject");
                }

                UserPrincipal principal = new UserPrincipal(userId);
                if (accessor.isMutable()) {
                    accessor.setUser(principal);
                    log.info("Successfully authenticated WebSocket session for user (mutable): {}", userId);
                } else {
                    StompHeaderAccessor mutableAccessor = StompHeaderAccessor.wrap(message);
                    mutableAccessor.setUser(principal);
                    mutableAccessor.setLeaveMutable(true);
                    log.info("Successfully authenticated WebSocket session for user (immutable wrap): {}", userId);
                    return org.springframework.messaging.support.MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
                }

            } catch (Exception e) {
                log.error("Failed WebSocket JWT validation: {}", e.getMessage());
                throw new MessageDeliveryException("Unauthorized: " + e.getMessage());
            }
        }

        return message;
    }
}
