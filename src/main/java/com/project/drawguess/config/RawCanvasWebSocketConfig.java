package com.project.drawguess.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

import com.project.drawguess.jwtfilter.JwtUtil;
import com.project.drawguess.websocket.BinaryCanvasHandler;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class RawCanvasWebSocketConfig {

    private final BinaryCanvasHandler binaryCanvasHandler;
    private final JwtUtil jwtUtil;

    @Bean
    public HandlerMapping rawCanvasWebSocketHandlerMapping() {
        WebSocketHttpRequestHandler handler = new WebSocketHttpRequestHandler(
                binaryCanvasHandler, new DefaultHandshakeHandler());
        handler.setHandshakeInterceptors(Collections.singletonList(new JwtHandshakeInterceptor(jwtUtil)));

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Collections.singletonMap("/ws-canvas-binary", handler));
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return mapping;
    }

    static class JwtHandshakeInterceptor implements HandshakeInterceptor {

        private final JwtUtil jwtUtil;

        JwtHandshakeInterceptor(JwtUtil jwtUtil) {
            this.jwtUtil = jwtUtil;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String query = request.getURI().getQuery();
            if (query == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            Map<String, String> params = parseQuery(query);
            String token = params.get("token");
            String roomCode = params.get("roomCode");

            if (token == null || roomCode == null || roomCode.isBlank()) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            try {
                String username = jwtUtil.extractUsernameFromAccessToken(token);
                if (!jwtUtil.isAccessTokenValid(token, username)) {
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                }
                attributes.put("username", username);
                attributes.put("roomCode", roomCode);
                return true;
            } catch (Exception e) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                WebSocketHandler wsHandler, Exception exception) {
        }

        private Map<String, String> parseQuery(String query) {
            Map<String, String> params = new HashMap<>();
            for (String pair : query.split("&")) {
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    params.put(pair.substring(0, idx), pair.substring(idx + 1));
                }
            }
            return params;
        }
    }
}
