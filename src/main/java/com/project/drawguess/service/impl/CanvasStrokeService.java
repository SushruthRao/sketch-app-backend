package com.project.drawguess.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CanvasStrokeService {

	private final Map<String, List<Map<String, Object>>> strokeHistory = new ConcurrentHashMap<>();

	public void addStroke(String roomCode, Map<String, Object> strokeData) {
		strokeHistory.computeIfAbsent(roomCode, k -> Collections.synchronizedList(new ArrayList<>())).add(strokeData);
	}

	public List<Map<String, Object>> getStrokes(String roomCode) {
		return strokeHistory.getOrDefault(roomCode, Collections.emptyList());
	}

	public void clearStrokes(String roomCode) {
		strokeHistory.remove(roomCode);
		log.info("Canvas strokes cleared for room {}", roomCode);
	}
}
