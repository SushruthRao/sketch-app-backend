package com.project.drawguess.websocket;

import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.project.drawguess.jwtfilter.JwtUtil;
import com.project.drawguess.service.impl.UserServiceImpl;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * STOMP channel interceptor that authenticates a client during CONNECT.
 *
 * If the JWT is missing, expired, or invalid the CONNECT frame is rejected
 * by throwing an exception, which causes Spring's broker relay to send a
 * STOMP ERROR frame back to the client. The @stomp/stompjs library reacts
 * to the ERROR frame by closing the socket, so malicious clients cannot
 * bypass auth by swallowing the error on the frontend.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserServiceImpl userServiceImpl;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String jwt = extractBearerToken(accessor);
        if (jwt == null) {
            log.warn("STOMP CONNECT rejected: missing Authorization header");
            throw new IllegalArgumentException("Missing authentication token");
        }

        try {
            String username = jwtUtil.extractUsernameFromAccessToken(jwt);
            if (username == null) {
                throw new IllegalArgumentException("Token has no subject");
            }

            UserDetails userDetails = userServiceImpl.loadUserByUsername(username);

            // Throws ExpiredJwtException if expired; returns false for other invalid states
            if (!jwtUtil.isAccessTokenValid(jwt, userDetails.getUsername())) {
                throw new IllegalArgumentException("Invalid authentication token");
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            accessor.setUser(authentication);
            log.info("WebSocket authenticated: {}", username);
            return message;

        } catch (ExpiredJwtException e) {
            log.info("STOMP CONNECT rejected: expired token ({})", e.getMessage());
            throw new IllegalArgumentException("Authentication token expired");
        } catch (JwtException e) {
            log.warn("STOMP CONNECT rejected: {} ({})", e.getClass().getSimpleName(), e.getMessage());
            throw new IllegalArgumentException("Invalid authentication token");
        } catch (IllegalArgumentException e) {
            // Rethrow our own auth-failure signals untouched
            throw e;
        } catch (Exception e) {
            log.error("Unexpected WebSocket auth error: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Authentication failed");
        }
    }

    private String extractBearerToken(StompHeaderAccessor accessor) {
        List<String> authorization = accessor.getNativeHeader("Authorization");
        if (authorization == null || authorization.isEmpty()) {
            return null;
        }
        String bearerToken = authorization.get(0);
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return null;
        }
        return bearerToken.substring(7);
    }
}