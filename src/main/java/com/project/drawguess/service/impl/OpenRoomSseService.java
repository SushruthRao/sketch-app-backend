package com.project.drawguess.service.impl;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.project.drawguess.dto.OpenRoomDto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OpenRoomSseService {
	private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
	private final RoomServiceImpl roomService;

	// Use @Lazy if RoomServiceImpl also injects OpenRoomSseService to avoid
	// circular dependency
	public OpenRoomSseService(@Lazy RoomServiceImpl roomService) {
		this.roomService = roomService;
	}

	public SseEmitter createEmitter() {
		SseEmitter emitter = new SseEmitter(0L);
		emitters.add(emitter);

		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(e -> emitters.remove(emitter));

		sendInitialData(emitter);
		return emitter;
	}

	private void sendInitialData(SseEmitter emitter) {
		try {
			List<OpenRoomDto> rooms = roomService.getOpenRooms();
			emitter.send(SseEmitter.event().name("room-update") // Consistent naming helps the frontend
					.data(rooms, MediaType.APPLICATION_JSON));
		} catch (IOException e) {
			log.error("Failed to send initial SSE data", e);
			emitters.remove(emitter);
		}
	}

	public void push(List<OpenRoomDto> rooms) {
		if (emitters.isEmpty())
			return;

		List<SseEmitter> disconnected = new ArrayList<>();

		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().data(rooms, MediaType.APPLICATION_JSON));
			} catch (Exception e) {
				// Client disconnected between the isEmpty check and the send
				disconnected.add(emitter);
			}
		}

		emitters.removeAll(disconnected);
		log.debug("Pushed public rooms update to {} client(s)", emitters.size() - disconnected.size());
	}
}
