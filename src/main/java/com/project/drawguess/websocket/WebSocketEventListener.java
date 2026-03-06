package com.project.drawguess.websocket;

import java.util.Map;

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

		Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
		if (sessionAttributes != null && Boolean.TRUE.equals(sessionAttributes.get("isCanvasConnection"))) {
			log.info("Canvas websocket disconnected: {}, skipping grace period", sessionId);
			return;
		}

		// event.getUser() is more reliable than headerAccessor.getUser() on disconnect
		// because Spring stores the principal at the session level, not in the message headers
		String username = event.getUser() != null ? event.getUser().getName() : null;
		log.info("Websocket disconnected : {}, user: {}", sessionId, username);
		roomServiceImpl.handlePlayerDisconnect(sessionId, username);
	}
}
