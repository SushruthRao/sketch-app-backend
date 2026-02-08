package com.project.drawguess.controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.project.drawguess.game.GameRoundManager;
import com.project.drawguess.model.Session;
import com.project.drawguess.model.User;
import com.project.drawguess.repository.UserRepository;
import com.project.drawguess.service.impl.CanvasStrokeService;
import com.project.drawguess.service.impl.RoomServiceImpl;
import com.project.drawguess.service.impl.SessionServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
@RequiredArgsConstructor
public class WebSocketController {

	private final SessionServiceImpl sessionServiceImpl;
	private final RoomServiceImpl roomServiceImpl;
	private final SimpMessagingTemplate messagingTemplate;
	private final UserRepository userRepository;
	private final GameRoundManager gameRoundManager;
	private final CanvasStrokeService canvasStrokeService;

	@MessageMapping("/room/{roomCode}/join")
	public void joinRoom(@DestinationVariable String roomCode, SimpMessageHeaderAccessor headerAccessor,
			Principal principal) {
		String wsSessionId = headerAccessor.getSessionId();
		String username = principal.getName();
		log.info("Joined room - Room - {}, User - {}, wsSessionId - {} ", roomCode, username, wsSessionId);

		roomServiceImpl.joinRoomViaWebSocket(roomCode, username, wsSessionId);

	}

	@MessageMapping("/room/{roomCode}/start")
	public void startGame(@DestinationVariable String roomCode, Principal principle) {
		if (principle == null) {
			log.error("Principle is null");
			return;
		}
		String username = principle.getName();
		log.info("Start game request, Roomcode - {}", roomCode);

		sessionServiceImpl.startSession(roomCode, username);

	}

	@MessageMapping("/room/{roomCode}/guess")
	public void sendGuess(@DestinationVariable String roomCode,
			SimpMessageHeaderAccessor headerAccessor,
			Principal principal,
			@Payload Map<String, String> payload) {
		if (principal == null) {
			log.error("Principal is null for guess");
			return;
		}
		String email = principal.getName();
		if (payload == null) {
			log.warn("Null payload for guess from {}", email);
			return;
		}
		String message = payload.get("message");

		if (message == null || message.trim().isEmpty()) {
			return;
		}

		if (message.length() > 250) {
			message = message.substring(0, 250);
		}

		User user = userRepository.findByEmail(email);
		if (user == null) {
			log.warn("User not found for email {}", email);
			return;
		}

		Session session = sessionServiceImpl.getActiveSession(roomCode);
		if (session == null) {
			log.warn("No active session for guess in room {}", roomCode);
			return;
		}

		gameRoundManager.processGuess(
				session.getSessionId(), roomCode,
				user.getUserId(), user.getUsername(), user.getEmail(),
				message.trim());
	}

	@MessageMapping("/room/{roomCode}/draw")
	public void handleDraw(@DestinationVariable String roomCode,
			Principal principal,
			@Payload Map<String, Object> strokeData) {
		if (principal == null) return;
		if (!gameRoundManager.isDrawerForRoom(roomCode, principal.getName())) {
			log.warn("Draw rejected for {} in room {} - not the drawer", principal.getName(), roomCode);
			return;
		}

		canvasStrokeService.addStroke(roomCode, strokeData);

		Map<String, Object> broadcast = new HashMap<>(strokeData);
		messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/draw", (Object) broadcast);
	}

	@MessageMapping("/room/{roomCode}/canvas-clear")
	public void handleCanvasClear(@DestinationVariable String roomCode,
			Principal principal) {
		if (principal == null) return;
		if (!gameRoundManager.isDrawerForRoom(roomCode, principal.getName())) return;

		canvasStrokeService.clearStrokes(roomCode);

		Map<String, Object> clearMsg = new HashMap<>();
		clearMsg.put("type", "CANVAS_CLEAR");
		messagingTemplate.convertAndSend("/topic/room/" + roomCode + "/draw", (Object) clearMsg);
	}

	@MessageExceptionHandler({ Exception.class, IllegalStateException.class })
	public void handleWebSocketException(Exception e, Principal principal) {
		log.error("Local WebSocket Error [User: {}]: {}", principal.getName(), e.getMessage());

		Map<String, Object> error = new HashMap<>();
		error.put("type", "ERROR");
		error.put("message", e.getMessage());

		messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", error);
	}
}
