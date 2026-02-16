package com.project.drawguess.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.drawguess.model.Room;
import com.project.drawguess.service.impl.RoomServiceImpl;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

	private final RoomServiceImpl roomServiceImpl;

	@PostMapping("/create")
	public ResponseEntity<?> createRoom(@AuthenticationPrincipal UserDetails userDetails)
			throws IllegalArgumentException {
		Room room = roomServiceImpl.createRoom(userDetails.getUsername());
		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("roomCode", room.getRoomCode());
		response.put("roomId", room.getRoomId());
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{roomCode}")
	public ResponseEntity<?> getRoomDetails(@PathVariable String roomCode) throws IllegalArgumentException {
		Room room = roomServiceImpl.getRoomByCode(roomCode);
		List<Map<String, Object>> players = roomServiceImpl.getActivePlayers(roomCode);
		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("room", Map.of("roomCode", room.getRoomCode(), "hostUsername", room.getHost().getUsername(),
				"status", room.getStatus().toString()));
		response.put("players", players);
		return ResponseEntity.ok(response);
	}

}