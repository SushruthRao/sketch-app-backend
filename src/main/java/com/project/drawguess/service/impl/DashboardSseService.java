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

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(0L); 
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        sendIndividualUpdate(emitter);

        return emitter;
    }

    @Scheduled(fixedRate = 5000)
    public void broadcastUserStats() {
        if (emitters.isEmpty()) return;

        List<UserStats> stats = userRepository.getUserDashboardStats();
        List<SseEmitter> disconnected = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("user-stats-update")
                        .data(stats, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
            	disconnected.add(emitter);
            }
        }
        emitters.removeAll(disconnected);
    }

    private void sendIndividualUpdate(SseEmitter emitter) {
        try {
            List<UserStats> stats = userRepository.getUserDashboardStats();
            emitter.send(SseEmitter.event()
                    .name("user-stats-update")
                    .data(stats, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
    }
}
