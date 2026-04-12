package com.project.drawguess.controller;

import com.project.drawguess.service.impl.DashboardSseService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;



/**
 * Thin HTTP endpoint that hands out an {@link SseEmitter} for the user-stats
 * dashboard. All broadcast logic (scheduled push, client lifecycle,
 * back-pressure handling) lives in {@link DashboardSseService}; the
 * controller is intentionally a 2-line facade so the streaming concern
 * stays out of the web layer.
 */
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
