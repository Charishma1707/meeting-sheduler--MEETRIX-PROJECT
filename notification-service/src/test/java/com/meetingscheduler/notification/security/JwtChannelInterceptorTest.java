package com.meetingscheduler.notification.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.security.Principal;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtChannelInterceptorTest {

    private JwtChannelInterceptor interceptor;
    private final String rawSecret = "ZGV2ZWxvcG1lbnQtc2VjcmV0LWtleS1tdXN0LWJlLWF0LWxlYXN0LTMyLWJ5dGVzCg==";
    private SecretKey key;

    @BeforeEach
    void setUp() {
        interceptor = new JwtChannelInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtSecret", rawSecret);
        interceptor.init();

        byte[] decodedKey = Base64.getDecoder().decode(rawSecret.trim());
        this.key = Keys.hmacShaKeyFor(decodedKey);
    }

    private String generateToken(String subject, long expiryMs) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(key)
                .compact();
    }

    @Test
    void preSend_withValidConnectToken_authenticatesUser() {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String token = generateToken(userId, 60000);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageChannel channel = Mockito.mock(MessageChannel.class);

        // Act
        Message<?> result = interceptor.preSend(message, channel);

        // Assert
        assertNotNull(result);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        Principal principal = resultAccessor.getUser();
        assertNotNull(principal);
        assertEquals(userId, principal.getName());
        assertTrue(principal instanceof UserPrincipal);
    }

    @Test
    void preSend_withMissingToken_throwsException() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = Mockito.mock(MessageChannel.class);

        // Act & Assert
        MessageDeliveryException ex = assertThrows(MessageDeliveryException.class, () -> {
            interceptor.preSend(message, channel);
        });
        assertTrue(ex.getMessage().contains("Missing or invalid Authorization header"));
    }

    @Test
    void preSend_withInvalidToken_throwsException() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer invalid-token-string");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = Mockito.mock(MessageChannel.class);

        // Act & Assert
        assertThrows(MessageDeliveryException.class, () -> {
            interceptor.preSend(message, channel);
        });
    }

    @Test
    void preSend_withExpiredToken_throwsException() {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String token = generateToken(userId, -10000);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = Mockito.mock(MessageChannel.class);

        // Act & Assert
        MessageDeliveryException ex = assertThrows(MessageDeliveryException.class, () -> {
            interceptor.preSend(message, channel);
        });
        assertTrue(ex.getMessage().contains("JWT expired"));
    }

    @Test
    void preSend_withNonConnectCommand_bypassesValidation() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = Mockito.mock(MessageChannel.class);

        // Act
        Message<?> result = interceptor.preSend(message, channel);

        // Assert
        assertNotNull(result);
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser());
    }
}
