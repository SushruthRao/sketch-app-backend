package com.project.drawguess.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.project.drawguess.service.impl.RoomServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener  {
	private final RoomServiceImpl roomServiceImpl;
	
	@EventListener
	public void handleWebSocketDisconnectListener(SessionDisconnectEvent event)
	{
		StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
		String sessionId = headerAccessor.getSessionId();
		log.info("Websocket disconnected : {} ", sessionId);
		roomServiceImpl.handlePlayerDisconnect(sessionId);
	}
}
