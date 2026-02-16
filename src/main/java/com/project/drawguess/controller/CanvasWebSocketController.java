package com.project.drawguess.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.project.drawguess.game.GameRoundManager;
import com.project.drawguess.model.Session;
import com.project.drawguess.service.impl.CanvasStrokeServiceImpl;
import com.project.drawguess.service.impl.SessionServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
@RequiredArgsConstructor
public class CanvasWebSocketController {

	private final SessionServiceImpl sessionServiceImpl;
	private final SimpMessagingTemplate messagingTemplate;
	private final GameRoundManager gameRoundManager;
	private final CanvasStrokeServiceImpl canvasStrokeService;

	@MessageMapping("/canvas/room/{roomCode}/draw")
	public void handleDraw(@DestinationVariable String roomCode,
			Principal principal,
			@Payload Map<String, Object> strokeData) {
		if (principal == null) return;

		Session session = sessionServiceImpl.getActiveSession(roomCode);
		if (session != null) {
			if (!gameRoundManager.isDrawerForRoom(roomCode, principal.getName())) {
				log.warn("Draw rejected for {} in room {} - not the drawer", principal.getName(), roomCode);
				return;
			}
		}
		canvasStrokeService.addStroke(roomCode, strokeData);

		Map<String, Object> broadcast = new HashMap<>(strokeData);
		broadcast.put("senderUsername", principal.getName());
		messagingTemplate.convertAndSend("/canvas-topic/room/" + roomCode + "/draw", (Object) broadcast);
	}

	@MessageMapping("/canvas/room/{roomCode}/clear")
	public void handleCanvasClear(@DestinationVariable String roomCode,
			Principal principal) {
		if (principal == null) return;
		if (!gameRoundManager.isDrawerForRoom(roomCode, principal.getName())) return;

		canvasStrokeService.clearStrokes(roomCode);

		Map<String, Object> clearMsg = new HashMap<>();
		clearMsg.put("type", "CANVAS_CLEAR");
		messagingTemplate.convertAndSend("/canvas-topic/room/" + roomCode + "/draw", (Object) clearMsg);
	}

	@MessageMapping("/canvas/room/{roomCode}/request-state")
	public void requestCanvas(@DestinationVariable String roomCode, Principal principal) {
		if (principal == null) return;
		List<Map<String, Object>> strokes = canvasStrokeService.getStrokes(roomCode);
		if (strokes != null && !strokes.isEmpty()) {
			Map<String, Object> canvasState = new HashMap<>();
			canvasState.put("type", "CANVAS_STATE");
			canvasState.put("strokes", new ArrayList<>(strokes));
			messagingTemplate.convertAndSendToUser(principal.getName(), "/canvas-queue/canvas-state", canvasState);
			log.info("Sent {} canvas strokes to {} for room {}", strokes.size(), principal.getName(), roomCode);
		}
	}

	@MessageExceptionHandler({ Exception.class })
	public void handleCanvasException(Exception e, Principal principal) {
		log.error("Canvas WebSocket Error [User: {}]: {}", principal.getName(), e.getMessage());
		Map<String, Object> error = new HashMap<>();
		error.put("type", "CANVAS_ERROR");
		error.put("message", e.getMessage());
		messagingTemplate.convertAndSendToUser(principal.getName(), "/canvas-queue/errors", error);
	}
}
