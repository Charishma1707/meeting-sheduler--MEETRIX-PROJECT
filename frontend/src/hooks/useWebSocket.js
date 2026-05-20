import { useCallback, useEffect, useRef, useState } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import toast from 'react-hot-toast';

const WS_URL = import.meta.env.VITE_WS_URL || '/ws';

export const useWebSocket = (isAuthenticated, accessToken) => {
  const clientRef = useRef(null);
  const [connected, setConnected] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [lastMessage, setLastMessage] = useState(null);
  const connectionAttemptRef = useRef(0);
  const maxReconnectAttemptsRef = useRef(5);

  const showNotificationToast = useCallback((notification) => {
    const icons = {
      INVITE_RECEIVED: 'Calendar',
      MEETING_CANCELLED: 'Cancelled',
      MEETING_UPDATED: 'Updated',
      RSVP_UPDATE: 'RSVP',
      REMINDER: 'Reminder',
    };
    toast(`${icons[notification.type] || 'Notice'}: ${notification.message}`, {
      duration: notification.type === 'REMINDER' ? 10000 : 5000,
      style: {
        background: notification.type === 'REMINDER' ? 'var(--color-warning)' : 'var(--color-surface)',
        color: notification.type === 'REMINDER' ? 'white' : 'var(--color-text-primary)',
        border: notification.type === 'REMINDER' ? '0' : '1px solid var(--color-border)',
        borderRadius: 'var(--radius-md)',
        boxShadow: 'var(--shadow-md)',
        fontWeight: 600,
      },
    });
  }, []);

  const handleConnectionError = useCallback((error, isInitial = false) => {
    console.error('WebSocket connection error:', error);
    setConnected(false);

    const errorMessage = error?.message || 'WebSocket connection failed';
    const statusCode = error?.status;

    if (statusCode === 401 || statusCode === 403) {
      toast.error('Session expired. Please sign in again.', { duration: 5000 });
      return;
    }

    if (connectionAttemptRef.current < maxReconnectAttemptsRef.current) {
      const retryMessage = `Connection failed. Retrying... (${connectionAttemptRef.current + 1}/${maxReconnectAttemptsRef.current})`;
      if (isInitial) {
        toast.loading(retryMessage, { duration: 3000 });
      }
    } else {
      toast.error('Unable to establish notifications. Some features may be limited.', { duration: 5000 });
    }
  }, []);

  useEffect(() => {
    if (!isAuthenticated || !accessToken) {
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
      }
      setConnected(false);
      return undefined;
    }

    connectionAttemptRef.current = 0;

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 5000,
      maxWebSocketFrameSize: 8 * 1024 * 1024, // 8MB
      debug: (msg) => {
        if (msg.includes('ERROR') || msg.includes('error')) {
          console.debug('STOMP Debug:', msg);
        }
      },
      onConnect: (frame) => {
        console.log('WebSocket connected successfully', frame);
        setConnected(true);
        connectionAttemptRef.current = 0;
        toast.success('Real-time notifications connected', { duration: 2000 });

        try {
          const subscription = client.subscribe(
            '/user/queue/notifications',
            (message) => {
              console.log('Received WebSocket message:', message);
              try {
                const notification = JSON.parse(message.body);
                console.log('Parsed notification:', notification);
                setLastMessage(notification);
                setNotifications((prev) => [notification, ...prev].slice(0, 50));
                showNotificationToast(notification);
              } catch (parseError) {
                console.error('Failed to parse WebSocket message:', parseError);
                console.error('Raw message body:', message.body);
              }
            },
            { id: 'notification-subscription' }
          );
          console.log('Successfully subscribed to /user/queue/notifications');
        } catch (subscribeError) {
          console.error('Failed to subscribe to notifications:', subscribeError);
          toast.error('Failed to subscribe to notifications', { duration: 3000 });
        }
      },
      onDisconnect: () => {
        console.log('WebSocket disconnected');
        setConnected(false);
      },
      onStompError: (frame) => {
        console.error('STOMP protocol error:', {
          command: frame.command,
          headers: frame.headers,
          body: frame.body,
        });
        handleConnectionError(
          new Error(`STOMP Error: ${frame.headers?.message || 'Unknown error'}`),
          connectionAttemptRef.current === 0
        );
      },
      onWebSocketError: (event) => {
        console.error('WebSocket error event:', event);
        connectionAttemptRef.current += 1;
        handleConnectionError(
          new Error('WebSocket protocol error'),
          connectionAttemptRef.current === 1
        );
      },
      onWebSocketClose: (event) => {
        console.log('WebSocket closed:', event.code, event.reason);
        setConnected(false);
      },
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    try {
      client.activate();
      clientRef.current = client;
    } catch (activationError) {
      console.error('Failed to activate WebSocket client:', activationError);
      handleConnectionError(activationError, true);
    }

    return () => {
      if (clientRef.current) {
        try {
          clientRef.current.deactivate();
          clientRef.current = null;
        } catch (deactivationError) {
          console.error('Error during WebSocket deactivation:', deactivationError);
        }
      }
      setConnected(false);
    };
  }, [isAuthenticated, accessToken, showNotificationToast, handleConnectionError]);

  const clearNotifications = useCallback(() => setNotifications([]), []);

  return { connected, notifications, lastMessage, clearNotifications };
};
