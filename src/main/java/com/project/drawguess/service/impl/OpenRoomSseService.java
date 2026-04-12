package com.project.drawguess.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.project.drawguess.dto.OpenRoomDto;

import lombok.extern.slf4j.Slf4j;

/**
 * Broadcasts open-room lobby updates to connected frontend clients via SSE.
 * Emitter lifecycle is managed so dead connections are reaped before the
 * next push, preventing {@code IOException} spam on the broadcast thread.
 */
@Slf4j
@Service
public class OpenRoomSseService {
	private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
	private final RoomServiceImpl roomService;

	// Use @Lazy because RoomServiceImpl also depends on this class → circular ref
	public OpenRoomSseService(@Lazy RoomServiceImpl roomService) {
		this.roomService = roomService;
	}

	public SseEmitter createEmitter() {
		SseEmitter emitter = new SseEmitter(0L);
		emitters.add(emitter);

		emitter.onCompletion(() -> {
			emitters.remove(emitter);
			log.debug("OpenRoom SSE emitter completed (remaining={})", emitters.size());
		});
		emitter.onTimeout(() -> {
			emitters.remove(emitter);
			log.debug("OpenRoom SSE emitter timed out (remaining={})", emitters.size());
			emitter.complete();
		});
		emitter.onError(e -> {
			emitters.remove(emitter);
			log.debug("OpenRoom SSE emitter error ({}): {}",
					e.getClass().getSimpleName(), e.getMessage());
		});

		sendInitialData(emitter);
		return emitter;
	}

	private void sendInitialData(SseEmitter emitter) {
		// IMPORTANT: this runs *before* the controller returns the emitter to
		// Spring, so we must NOT call completeWithError() here — Spring hasn't
		// attached its async handler yet, and completing the emitter now makes
		// it throw "ResponseBodyEmitter has already completed" when the
		// dispatcher later tries to write the response body.
		try {
			List<OpenRoomDto> rooms = roomService.getOpenRooms();
			emitter.send(SseEmitter.event()
					.name("room-update")
					.data(rooms, MediaType.APPLICATION_JSON));
		} catch (IOException e) {
			log.debug("Failed to send initial open-room SSE data: {}", e.getMessage());
			emitters.remove(emitter);
		} catch (IllegalStateException e) {
			// Emitter already completed by another thread (onError/onTimeout ran)
			log.debug("Initial open-room SSE send skipped (emitter already completed): {}", e.getMessage());
			emitters.remove(emitter);
		} catch (Exception e) {
			log.error("Unexpected error sending initial open-room SSE data: {}", e.getMessage(), e);
			emitters.remove(emitter);
		}
	}

	public void push(List<OpenRoomDto> rooms) {
		if (emitters.isEmpty()) return;

		List<SseEmitter> disconnected = new ArrayList<>();

		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().data(rooms, MediaType.APPLICATION_JSON));
			} catch (IOException e) {
				// Client disconnected between the isEmpty check and the send — expected
				disconnected.add(emitter);
				log.debug("Dropping dead open-room SSE emitter: {}", e.getMessage());
			} catch (IllegalStateException e) {
				// Race: the emitter finished between our iteration and this send
				disconnected.add(emitter);
				log.debug("Open-room SSE emitter already completed, skipping: {}", e.getMessage());
			} catch (Exception e) {
				disconnected.add(emitter);
				log.warn("Unexpected error pushing open-room SSE ({}): {}",
						e.getClass().getSimpleName(), e.getMessage());
			}
		}

		emitters.removeAll(disconnected);
		log.debug("Pushed open-rooms update to {} client(s)", emitters.size());
	}
}
