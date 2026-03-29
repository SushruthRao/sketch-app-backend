package com.project.drawguess.service;

import com.project.drawguess.dto.PublicRoomDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages open SSE connections for the public rooms list.
 *
 * Lives as a singleton bean so both RoomController (which registers new
 * emitters) and RoomServiceImpl (which triggers pushes) can share the same
 * emitter list without any circular dependency between them.
 */
@Slf4j
@Service
public class PublicRoomsSseService {

    // CopyOnWriteArrayList so we can iterate and remove safely from concurrent threads
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Creates a new emitter, registers cleanup callbacks, and adds it to the
     * list. The caller is responsible for sending an initial snapshot before
     * returning the emitter to the client.
     */
    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(0L); // 0 = no server-side timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    /**
     * Pushes a fresh room list to every connected client.
     * Dead emitters (closed tabs, network drops) are pruned each call.
     */
    public void push(List<PublicRoomDto> rooms) {
        if (emitters.isEmpty()) return;

        List<SseEmitter> dead = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(rooms, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                // Client disconnected between the isEmpty check and the send
                dead.add(emitter);
            }
        }

        emitters.removeAll(dead);
        log.debug("Pushed public rooms update to {} client(s)", emitters.size() - dead.size());
    }
}
