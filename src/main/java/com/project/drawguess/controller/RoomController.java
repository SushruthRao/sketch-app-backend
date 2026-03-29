package com.project.drawguess.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.project.drawguess.dto.PublicRoomDto;
import com.project.drawguess.model.Room;
import com.project.drawguess.service.PublicRoomsSseService;
import com.project.drawguess.service.impl.RoomServiceImpl;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

	private final RoomServiceImpl roomServiceImpl;
	private final PublicRoomsSseService publicRoomsSseService;

	@PostMapping("/create")
	public ResponseEntity<?> createRoom(
			@AuthenticationPrincipal UserDetails userDetails,
			@RequestBody(required = false) Map<String, Object> body)
			throws IllegalArgumentException {
		boolean isPublic = body == null || !Boolean.FALSE.equals(body.get("isPublic"));
		Room room = roomServiceImpl.createRoom(userDetails.getUsername(), isPublic);
		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("roomCode", room.getRoomCode());
		response.put("roomId", room.getRoomId());
		return ResponseEntity.ok(response);
	}

	@GetMapping("/public")
	public ResponseEntity<?> getPublicRooms() {
		List<PublicRoomDto> rooms = roomServiceImpl.getPublicRooms();
		Map<String, Object> response = new HashMap<>();
		response.put("success", true);
		response.put("rooms", rooms);
		return ResponseEntity.ok(response);
	}

	/**
	 * SSE stream for the public rooms list shown on the home page.
	 *
	 * Sends the current room list immediately on connect, then pushes a fresh
	 * list every time a room is created, joined, started, or closed — driven by
	 * RoomServiceImpl.broadcastLobbyUpdate() calling PublicRoomsSseService.push().
	 * The client keeps this connection open for the lifetime of the home page.
	 */
	@GetMapping(value = "/public/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter streamPublicRooms() {
		SseEmitter emitter = publicRoomsSseService.createEmitter();

		// Fire the current snapshot straight away so the UI isn't blank on load
		try {
			List<PublicRoomDto> rooms = roomServiceImpl.getPublicRooms();
			emitter.send(SseEmitter.event().data(rooms, MediaType.APPLICATION_JSON));
		} catch (IOException e) {
			// Client already gone — the onError callback will clean up the emitter
		}

		return emitter;
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
