package com.project.drawguess.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.drawguess.model.Session;
import com.project.drawguess.service.impl.SessionServiceImpl;

import lombok.RequiredArgsConstructor;

/**
 * Exposes the currently active game session (if any) for a given room code.
 * The frontend hits this on room load to decide between "show lobby" and
 * "show in-progress game UI" before connecting the WebSocket.
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {
	private final SessionServiceImpl sessionServiceImpl;
	
	@GetMapping("/room/{roomCode}")
	public ResponseEntity<?> getActiveSession(@PathVariable String roomCode) throws IllegalStateException
	{
			Session session = sessionServiceImpl.getActiveSession(roomCode);
			if(session == null)
			{
				return ResponseEntity.ok(Map.of("success", true, "hasActiveSession", false));
			}
			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("session",
					Map.of("id", session.getSessionId(),
						   "status",session.getStatus(),
						   "totalRounds", session.getTotalRounds(),
						   "currentRound", session.getCurrentRound(),
						   "startedAt", session.getStartedAt().toString())
					);
			response.put("players", sessionServiceImpl.getSessionPlayers(session.getSessionId()));
			return ResponseEntity.ok(response);
		
	}
}