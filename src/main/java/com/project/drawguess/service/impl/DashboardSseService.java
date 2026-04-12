package com.project.drawguess.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.project.drawguess.dto.UserStats;
import com.project.drawguess.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DashboardSseService {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final UserRepository userRepository;

    public DashboardSseService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Creates and registers a new SSE emitter with an infinite timeout (0L).
     * Lifecycle callbacks remove the emitter from the active set so we never
     * try to write to a dead connection on the next broadcast.
     */
    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("Dashboard SSE emitter completed (remaining={})", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("Dashboard SSE emitter timed out (remaining={})", emitters.size());
            emitter.complete();
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            // Disconnects / network resets are expected; log at debug level to avoid log spam
            log.debug("Dashboard SSE emitter error ({}): {}", e.getClass().getSimpleName(), e.getMessage());
        });

        sendIndividualUpdate(emitter);
        return emitter;
    }

    @Scheduled(fixedRate = 5000)
    public void broadcastUserStats() {
        if (emitters.isEmpty()) return;

        final List<UserStats> stats;
        try {
            stats = userRepository.getUserDashboardStats();
        } catch (Exception e) {
            // A DB failure must not take the scheduler down — it would stop all future broadcasts
            log.error("Failed to load dashboard stats for broadcast: {}", e.getMessage(), e);
            return;
        }

        List<SseEmitter> disconnected = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("user-stats-update")
                        .data(stats, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                // Client went away between ticks — expected, drop the emitter
                disconnected.add(emitter);
                log.debug("Dropping dead dashboard SSE emitter: {}", e.getMessage());
            } catch (IllegalStateException e) {
                // Race with onCompletion/onTimeout — emitter already closed
                disconnected.add(emitter);
                log.debug("Dashboard SSE emitter already completed, skipping: {}", e.getMessage());
            } catch (Exception e) {
                disconnected.add(emitter);
                log.warn("Unexpected error sending dashboard SSE ({}): {}",
                        e.getClass().getSimpleName(), e.getMessage());
            }
        }
        emitters.removeAll(disconnected);
    }

    private void sendIndividualUpdate(SseEmitter emitter) {
        // Called from createEmitter() *before* the controller returns the
        // emitter to Spring. Must NOT call completeWithError() here — doing
        // so puts the emitter into a completed state before Spring attaches
        // its async handler, producing "ResponseBodyEmitter has already
        // completed" from the dispatcher.
        try {
            List<UserStats> stats = userRepository.getUserDashboardStats();
            emitter.send(SseEmitter.event()
                    .name("user-stats-update")
                    .data(stats, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitters.remove(emitter);
            log.debug("Initial dashboard SSE send failed: {}", e.getMessage());
        } catch (IllegalStateException e) {
            emitters.remove(emitter);
            log.debug("Initial dashboard SSE skipped (emitter already completed): {}", e.getMessage());
        } catch (Exception e) {
            emitters.remove(emitter);
            log.warn("Failed to send initial dashboard stats: {}", e.getMessage(), e);
        }
    }
}
