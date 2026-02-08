package com.project.drawguess.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

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
		config.enableSimpleBroker("/topic", "/queue"); // server -> client (broadcast)
		config.setApplicationDestinationPrefixes("/app"); // client -> server
		config.setUserDestinationPrefix("/user");
	}
	
	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry)
	{
		registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS(); // ws://localhost:8080/ws 
	}
	
	@Override
	public void configureClientInboundChannel(ChannelRegistration registration)
	{
		registration.interceptors(webSocketAuthInterceptor);
	}

}
