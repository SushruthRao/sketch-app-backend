package com.project.drawguess.config;

import java.util.Map;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.project.drawguess.websocket.WebSocketAuthInterceptor;

import lombok.RequiredArgsConstructor;


@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final WebSocketAuthInterceptor webSocketAuthInterceptor;

	@Override
	public void configureMessageBroker(MessageBrokerRegistry config)
	{
		config.enableSimpleBroker("/topic", "/queue", "/canvas-topic", "/canvas-queue"); // server -> client (broadcast)
		config.setApplicationDestinationPrefixes("/app"); // client -> server
		config.setUserDestinationPrefix("/user");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry)
	{
		registry.addEndpoint("/ws")
			.setAllowedOrigins("https://sketch-app-frontend.vercel.app", "http://localhost:5173")
			.withSockJS();

		registry.addEndpoint("/ws-canvas")
			.setAllowedOrigins("https://sketch-app-frontend.vercel.app", "http://localhost:5173")
			.addInterceptors(new CanvasHandshakeInterceptor())
			.withSockJS();
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration)
	{
		registration.interceptors(webSocketAuthInterceptor);
	}

	static class CanvasHandshakeInterceptor implements HandshakeInterceptor {
		@Override
		public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
				WebSocketHandler wsHandler, Map<String, Object> attributes) {
			attributes.put("isCanvasConnection", true);
			return true;
		}

		@Override
		public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
				WebSocketHandler wsHandler, Exception exception) {
		}
	}

}
