package com.project.drawguess.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	@GetMapping("/health")
	public ResponseEntity<?> health() {
		return ResponseEntity.ok(Map.of(
			"status", "UP",
			"lastPinged", Instant.now().toString()
		));
	}
}
