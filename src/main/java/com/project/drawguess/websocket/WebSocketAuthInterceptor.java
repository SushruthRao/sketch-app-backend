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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserServiceImpl userServiceImpl;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            String jwt = null;

            // First try STOMP Authorization header
            List<String> authorization = accessor.getNativeHeader("Authorization");
            log.info("STOMP Connect Authorization: {}", authorization);

            if (authorization != null && !authorization.isEmpty()) {
                String bearerToken = authorization.get(0);
                if (bearerToken.startsWith("Bearer ")) {
                    jwt = bearerToken.substring(7);
                }
            }

            if (jwt != null) {
                try {
                    String username = jwtUtil.extractUsernameFromAccessToken(jwt);
                    if (username != null) {
                        UserDetails userDetails = userServiceImpl.loadUserByUsername(username);

                        if (jwtUtil.isAccessTokenValid(jwt, userDetails.getUsername())) {
                            UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                            accessor.setUser(authentication);
                            log.info("Successfully authenticated WebSocket user: {}", username);
                        }
                    }
                } catch (Exception e) {
                    log.error("WebSocket Auth Error: {}", e.getMessage());
                }
            }
        }
        return message;
    }
}