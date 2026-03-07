package com.project.drawguess.game;


import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import lombok.Data;

@Data
public class RoundState {
	private final Long sessionId;
	private final int roundNumber;
	private final Long drawerId;
	private final String drawerUsername;
	private final String drawerEmail;
	private final String word;
	private final Instant startedAt;
	/** Maps userId → seconds elapsed when they guessed correctly */
	private final Map<Long, Long> correctGuessers = new ConcurrentHashMap<>();
	private final int totalGuessers;
	private ScheduledFuture<?> timerTask;

	public RoundState(Long sessionId, int roundNumber, Long drawerId,
			String drawerUsername, String drawerEmail,
			String word, int totalGuessers) {
		this.sessionId = sessionId;
		this.roundNumber = roundNumber;
		this.drawerId = drawerId;
		this.drawerUsername = drawerUsername;
		this.drawerEmail = drawerEmail;
		this.word = word;
		this.startedAt = Instant.now();
		this.totalGuessers = totalGuessers;
	}

	public boolean hasEveryoneGuessed() {
		return correctGuessers.size() >= totalGuessers;
	}

	public long getElapsedSeconds() {
		return Duration.between(startedAt, Instant.now()).getSeconds();
	}

	public boolean hasPlayerGuessed(Long userId) {
		return correctGuessers.containsKey(userId);
	}

	public void addCorrectGuesser(Long userId, long secondsTaken) {
		correctGuessers.put(userId, secondsTaken);
	}

	/** Returns just the user IDs of correct guessers (for live broadcasts). */
	public Set<Long> getCorrectGuesserIds() {
		return correctGuessers.keySet();
	}
}