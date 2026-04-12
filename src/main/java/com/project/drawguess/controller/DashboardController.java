package com.project.drawguess.controller;

import com.project.drawguess.dto.UserStats;
import com.project.drawguess.repository.UserRepository;
import com.project.drawguess.service.impl.DashboardSseService;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;



@RestController
@RequestMapping("/dashboard")
@Slf4j
public class DashboardController {

    private final DashboardSseService dashboardSseService;

    public DashboardController(DashboardSseService dashboardSseService) {
        this.dashboardSseService = dashboardSseService;
    }

    @GetMapping(value = "/user-stats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUserStats() {
        return dashboardSseService.createEmitter();
    }
}
