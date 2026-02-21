package com.project.drawguess.game;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.project.drawguess.enums.RoomStatus;
import com.project.drawguess.enums.SessionStatus;
import com.project.drawguess.model.Room;
import com.project.drawguess.model.Session;
import com.project.drawguess.model.UserSession;
import com.project.drawguess.repository.RoomRepository;
import com.project.drawguess.repository.SessionRepository;
import com.project.drawguess.repository.UserSessionRepository;
import com.project.drawguess.service.impl.CanvasStrokeServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameRoundManager {

	private final SessionRepository sessionRepository;
	private final UserSessionRepository userSessionRepository;
	private final RoomRepository roomRepository;
	private final SimpMessagingTemplate messagingTemplate;
	private final CanvasStrokeServiceImpl canvasStrokeService;

	private final Map<Long, RoundState> activeRounds = new ConcurrentHashMap<>();
	private final Map<Long, List<Long>> drawerOrders = new ConcurrentHashMap<>();
	private final Map<Long, Integer> drawerRotationCounters = new ConcurrentHashMap<>();
	private final Map<String, Long> roomCodeToSessionId = new ConcurrentHashMap<>();
	private final Map<Long, ScheduledFuture<?>> pendingNextRoundTasks = new ConcurrentHashMap<>();

	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

	private static final int MAX_START_RETRIES = 6;
	private static final int RETRY_INTERVAL_SECONDS = 5;

	
	@Value("${app.gameroundmanager.round-duration-seconds:40}")
	private int ROUND_DURATION_SECONDS;
	
	@Value("${app.gameroundmanager.max-guesser-points:500}")
	private int MAX_GUESSER_POINTS;
	
	@Value("${app.gameroundmanager.drawer-points-per-guess:100}")
	private int DRAWER_POINTS_PER_GUESS;
	
	@Value("${app.gameroundmanager.delay-between-round-seconds:5}")
	private int DELAY_BETWEEN_ROUNDS_SECONDS;

	public void initializeGame(Session session, String roomCode) {
		Long sessionId = session.getSessionId();
		List<UserSession> activePlayers = userSessionRepository.findActiveUsersBySessionId(sessionId);

		List<Long> drawerOrder = new ArrayList<>();
		for (UserSession us : activePlayers) {
			drawerOrder.add(us.getUser().getUserId());
		}
		drawerOrders.put(sessionId, drawerOrder);
		roomCodeToSessionId.put(roomCode, sessionId);

		log.info("Game initialized for session {}. Drawer order: {}", sessionId, drawerOrder);

		scheduler.schedule(() -> {
			try {
				startNextRound(sessionId, roomCode);
			} catch (Exception e) {
				log.error("Error starting round for session {}: {}", sessionId, e.getMessage(), e);
			}
		}, 2, TimeUnit.SECONDS);
	}

	public synchronized void startNextRound(Long sessionId, String roomCode) {
		startNextRound(sessionId, roomCode, 0);
	}

	private synchronized void startNextRound(Long sessionId, String roomCode, int retryCount) {
		pendingNextRoundTasks.remove(sessionId);

		Session session = sessionRepository.findById(sessionId).orElse(null);
		if (session == null || session.getStatus() != SessionStatus.ACTIVE) {
			log.info("Session {} is not active, cannot start next round", sessionId);
			cleanup(sessionId);
			return;
		}

		int nextRound = session.getCurrentRound() + 1;

		if (nextRound > session.getTotalRounds()) {
			log.info("All {} rounds complete for session {}", session.getTotalRounds(), sessionId);
			cleanup(sessionId);
			broadcastAllRoundsComplete(roomCode, session);
			finalizeSession(session, roomCode);
			return;
		}

		List<UserSession> activePlayers = userSessionRepository.findActiveUsersBySessionId(sessionId);

		if (activePlayers.size() < 2) {
			if (retryCount < MAX_START_RETRIES) {
				log.info("Less than 2 active players for session {}, retrying in {}s (attempt {}/{})",
						sessionId, RETRY_INTERVAL_SECONDS, retryCount + 1, MAX_START_RETRIES);
				scheduleRetry(sessionId, roomCode, retryCount + 1);
			} else {
				log.warn("Less than 2 active players for session {} after {} retries, giving up", sessionId, MAX_START_RETRIES);
			}
			return;
		}

		List<Long> originalOrder = drawerOrders.get(sessionId);
		if (originalOrder == null || originalOrder.isEmpty()) {
			log.error("No drawer order found for session {}", sessionId);
			return;
		}

		Set<Long> activeIds = activePlayers.stream()
				.map(us -> us.getUser().getUserId())
				.collect(Collectors.toSet());

		List<Long> activeOrder = originalOrder.stream()
				.filter(activeIds::contains)
				.collect(Collectors.toList());

		if (activeOrder.isEmpty()) {
			if (retryCount < MAX_START_RETRIES) {
				log.info("No active players in drawer order for session {}, retrying in {}s (attempt {}/{})",
						sessionId, RETRY_INTERVAL_SECONDS, retryCount + 1, MAX_START_RETRIES);
				scheduleRetry(sessionId, roomCode, retryCount + 1);
			} else {
				log.error("No active players in drawer order for session {} after {} retries", sessionId, MAX_START_RETRIES);
			}
			return;
		}

		int rotationIndex = drawerRotationCounters.getOrDefault(sessionId, 0);
		Long drawerId = activeOrder.get(rotationIndex % activeOrder.size());
		drawerRotationCounters.put(sessionId, rotationIndex + 1);

		UserSession drawerSession = activePlayers.stream()
				.filter(us -> us.getUser().getUserId().equals(drawerId))
				.findFirst().orElse(null);

		if (drawerSession == null) {
			log.error("Drawer {} not found in active players despite filtering", drawerId);
			return;
		}

		String word = WordProvider.getRandomWord();

		canvasStrokeService.clearStrokes(roomCode);

		Map<String, Object> clearMsg = new HashMap<>();
		clearMsg.put("type", "CANVAS_CLEAR");
		messagingTemplate.convertAndSend("/canvas-topic/room/" + roomCode + "/draw", (Object) clearMsg);

		session.setCurrentRound(nextRound);
		sessionRepository.save(session);

		int guesserCount = activePlayers.size() - 1;
		RoundState roundState = new RoundState(
				sessionId, nextRound, drawerId,
				drawerSession.getUser().getUsername(),
				drawerSession.getUser().getEmail(),
				word, guesserCount);
		activeRounds.put(sessionId, roundState);

		ScheduledFuture<?> timerTask = scheduler.schedule(() -> {
			try {
				endRound(sessionId, roomCode, "TIME_UP");
			} catch (Exception e) {
				log.error("Error ending round (TIME_UP) for session {}: {}", sessionId, e.getMessage(), e);
			}
		}, ROUND_DURATION_SECONDS, TimeUnit.SECONDS);
		roundState.setTimerTask(timerTask);

		broadcastRoundStarted(roomCode, roundState, activePlayers, session);
		sendWordToDrawer(drawerSession.getUser().getEmail(), word, nextRound);

		log.info("Round {} started. Drawer: {}, Word: {}",
				nextRound, drawerSession.getUser().getUsername(), word);
	}

	public void processGuess(Long sessionId, String roomCode,
			Long userId, String username, String email,
			String message) {
		RoundState round = activeRounds.get(sessionId);

		if (round == null) {
			broadcastChatMessage(roomCode, username, message);
			return;
		}

		if (userId.equals(round.getDrawerId())) {
			sendPrivateError(email, "You are drawing, cannot send message when drawing");
			return;
		}

		if (round.hasPlayerGuessed(userId)) {
			sendPrivateError(email, "Already guessed");
			return;
		}

		if (isCorrectGuess(message, round.getWord())) {
			handleCorrectGuess(sessionId, roomCode, round, userId, username);
			return;
		}

		if (containsWord(message, round.getWord())) {
			sendPrivateError(email, "Almost guessed");
			return;
		}

		broadcastChatMessage(roomCode, username, message);
	}

	public void handleDrawerDisconnect(Long sessionId, String roomCode, Long userId) {
		RoundState round = activeRounds.get(sessionId);
		if (round != null && round.getDrawerId().equals(userId)) {
			log.info("Drawer {} disconnected during round {}",
					round.getDrawerUsername(), round.getRoundNumber());
			endRound(sessionId, roomCode, "DRAWER_LEFT");
		}
	}

	public void handleGuesserDisconnect(Long sessionId, String roomCode) {
		RoundState round = activeRounds.get(sessionId);
		if (round == null) return;

		long activeCount = userSessionRepository.countActivePlayersBySessionId(sessionId);
		if (activeCount < 2) {
			log.info("Less than 2 active players, ending round");
			endRound(sessionId, roomCode, "NOT_ENOUGH_PLAYERS");
			return;
		}

		List<UserSession> active = userSessionRepository.findActiveUsersBySessionId(sessionId);
		long remainingGuessers = active.stream()
				.filter(us -> !us.getUser().getUserId().equals(round.getDrawerId()))
				.filter(us -> !round.hasPlayerGuessed(us.getUser().getUserId()))
				.count();
		if (remainingGuessers == 0) {
			endRound(sessionId, roomCode, "ALL_GUESSED");
		}
	}

	public Map<String, Object> getRoundStateForReconnection(Long sessionId) {
		RoundState round = activeRounds.get(sessionId);
		if (round == null) return null;

		Map<String, Object> state = new HashMap<>();
		state.put("roundNumber", round.getRoundNumber());
		state.put("drawerUsername", round.getDrawerUsername());
		state.put("drawerId", round.getDrawerId());
		state.put("wordLength", round.getWord().length());
		state.put("elapsedSeconds", round.getElapsedSeconds());
		state.put("durationSeconds", ROUND_DURATION_SECONDS);

		Session session = sessionRepository.findById(sessionId).orElse(null);
		if (session != null) {
			state.put("totalRounds", session.getTotalRounds());
		}

		List<UserSession> activePlayers = userSessionRepository.findActiveUsersBySessionId(sessionId);
		List<Map<String, Object>> players = new ArrayList<>();
		List<String> correctGuesserUsernames = new ArrayList<>();
		int guesserCount = 0;
		for (UserSession us : activePlayers) {
			Map<String, Object> playerData = new HashMap<>();
			playerData.put("userId", us.getUser().getUserId());
			playerData.put("username", us.getUser().getUsername());
			playerData.put("score", us.getScore());
			players.add(playerData);
			if (!us.getUser().getUserId().equals(round.getDrawerId())) {
				guesserCount++;
				if (round.hasPlayerGuessed(us.getUser().getUserId())) {
					correctGuesserUsernames.add(us.getUser().getUsername());
				}
			}
		}
		state.put("players", players);
		state.put("totalGuessers", guesserCount);
		state.put("correctGuessers", correctGuesserUsernames);

		return state;
	}

	public Map<String, Object> getBetweenRoundsState(Long sessionId) {
		Session session = sessionRepository.findById(sessionId).orElse(null);
		if (session == null) return null;

		Map<String, Object> state = new HashMap<>();
		state.put("roundNumber", session.getCurrentRound());
		state.put("totalRounds", session.getTotalRounds());
		state.put("betweenRounds", true);

		List<UserSession> activePlayers = userSessionRepository.findActiveUsersBySessionId(sessionId);
		List<Map<String, Object>> players = new ArrayList<>();
		for (UserSession us : activePlayers) {
			Map<String, Object> playerData = new HashMap<>();
			playerData.put("userId", us.getUser().getUserId());
			playerData.put("username", us.getUser().getUsername());
			playerData.put("score", us.getScore());
			players.add(playerData);
		}
		state.put("players", players);
		return state;
	}

	public String getWordForDrawer(Long sessionId, Long userId) {
		RoundState round = activeRounds.get(sessionId);
		if (round != null && round.getDrawerId().equals(userId)) {
			return round.getWord();
		}
		return null;
	}

	public boolean isDrawerForRoom(String roomCode, String email) {
		Long sessionId = roomCodeToSessionId.get(roomCode);
		if (sessionId == null) return false;
		RoundState round = activeRounds.get(sessionId);
		if (round == null) return false;
		return email.equals(round.getDrawerEmail());
	}

	private synchronized void endRound(Long sessionId, String roomCode, String reason) {
		RoundState round = activeRounds.remove(sessionId);
		if (round == null) {
			log.info("Round already ended for session {}", sessionId);
			return;
		}

		if (round.getTimerTask() != null && !round.getTimerTask().isDone()) {
			round.getTimerTask().cancel(false);
		}

		log.info("Round {} ended for session {}. Reason: {}. Correct guessers: {}",
				round.getRoundNumber(), sessionId, reason, round.getCorrectGuessers().size());

		broadcastRoundEnded(roomCode, round, reason);

		scheduler.schedule(() -> {
			try {
				startNextRound(sessionId, roomCode);
			} catch (Exception e) {
				log.error("Error starting next round for session {}: {}", sessionId, e.getMessage(), e);
			}
		}, DELAY_BETWEEN_ROUNDS_SECONDS, TimeUnit.SECONDS);
	}

	private void handleCorrectGuess(Long sessionId, String roomCode,
			RoundState round, Long userId, String username) {
		round.addCorrectGuesser(userId);

		long elapsed = round.getElapsedSeconds();
		int guesserScore = Math.max(50, (int) (MAX_GUESSER_POINTS
				- (elapsed * MAX_GUESSER_POINTS / ROUND_DURATION_SECONDS)));

		List<UserSession> activePlayers = userSessionRepository.findActiveUsersBySessionId(sessionId);

		activePlayers.stream()
				.filter(us -> us.getUser().getUserId().equals(userId))
				.findFirst()
				.ifPresent(guesserSession -> {
					guesserSession.addScore(guesserScore);
					userSessionRepository.save(guesserSession);
				});

		activePlayers.stream()
				.filter(us -> us.getUser().getUserId().equals(round.getDrawerId()))
				.findFirst()
				.ifPresent(drawerSession -> {
					drawerSession.addScore(DRAWER_POINTS_PER_GUESS);
					userSessionRepository.save(drawerSession);
				});

		Map<String, Object> msg = new HashMap<>();
		msg.put("type", "CORRECT_GUESS");
		msg.put("username", username);
		msg.put("score", guesserScore);
		msg.put("correctCount", round.getCorrectGuessers().size());
		msg.put("totalGuessers", round.getTotalGuessers());
		msg.put("timestamp", LocalDateTime.now().toString());
		messagingTemplate.convertAndSend("/topic/room/" + roomCode, (Object) msg);

		log.info("{} guessed correctly! +{} points. ({}/{})",
				username, guesserScore, round.getCorrectGuessers().size(), round.getTotalGuessers());

		if (round.hasEveryoneGuessed()) {
			endRound(sessionId, roomCode, "ALL_GUESSED");
		}
	}

	private boolean containsWord(String message, String word) {
		return message.toLowerCase().contains(word.toLowerCase());
	}

	private boolean isCorrectGuess(String message, String word) {
		return message.trim().equalsIgnoreCase(word);
	}

	private void broadcastRoundStarted(String roomCode, RoundState round,
			List<UserSession> players, Session session) {
		Map<String, Object> msg = new HashMap<>();
		msg.put("type", "ROUND_STARTED");
		msg.put("roundNumber", round.getRoundNumber());
		msg.put("totalRounds", session.getTotalRounds());
		msg.put("drawerUsername", round.getDrawerUsername());
		msg.put("drawerId", round.getDrawerId());
		msg.put("wordLength", round.getWord().length());
		msg.put("durationSeconds", ROUND_DURATION_SECONDS);
		msg.put("timestamp", LocalDateTime.now().toString());

		List<Map<String, Object>> scores = new ArrayList<>();
		for (UserSession us : players) {
			Map<String, Object> scoreData = new HashMap<>();
			scoreData.put("userId", us.getUser().getUserId());
			scoreData.put("username", us.getUser().getUsername());
			scoreData.put("score", us.getScore());
			scores.add(scoreData);
		}
		msg.put("players", scores);

		messagingTemplate.convertAndSend("/topic/room/" + roomCode, (Object) msg);
	}

	private void sendWordToDrawer(String drawerEmail, String word, int roundNumber) {
		Map<String, Object> msg = new HashMap<>();
		msg.put("type", "YOUR_WORD");
		msg.put("word", word);
		msg.put("roundNumber", roundNumber);
		messagingTemplate.convertAndSendToUser(drawerEmail, "/queue/word", msg);
	}

	private void broadcastRoundEnded(String roomCode, RoundState round, String reason) {
		Map<String, Object> msg = new HashMap<>();
		msg.put("type", "ROUND_ENDED");
		msg.put("roundNumber", round.getRoundNumber());
		msg.put("word", round.getWord());
		msg.put("reason", reason);
		msg.put("correctGuessers", new ArrayList<>(round.getCorrectGuessers()));
		msg.put("drawerUsername", round.getDrawerUsername());
		msg.put("timestamp", LocalDateTime.now().toString());
		messagingTemplate.convertAndSend("/topic/room/" + roomCode, (Object) msg);
	}

	private void broadcastChatMessage(String roomCode, String username, String message) {
		Map<String, Object> msg = new HashMap<>();
		msg.put("type", "CHAT_MESSAGE");
		msg.put("username", username);
		msg.put("message", message);
		msg.put("timestamp", LocalDateTime.now().toString());
		messagingTemplate.convertAndSend("/topic/room/" + roomCode, (Object) msg);
	}

	private void broadcastAllRoundsComplete(String roomCode, Session session) {
		List<UserSession> allPlayers = userSessionRepository.findBySession(session);
		List<Map<String, Object>> finalScores = new ArrayList<>();
		for (UserSession us : allPlayers) {
			Map<String, Object> scoreData = new HashMap<>();
			scoreData.put("username", us.getUser().getUsername());
			scoreData.put("score", us.getScore());
			finalScores.add(scoreData);
		}
		finalScores.sort((a, b) -> Integer.compare(
				(Integer) b.get("score"), (Integer) a.get("score")));

		String winner = finalScores.isEmpty() ? null : (String) finalScores.get(0).get("username");

		Map<String, Object> msg = new HashMap<>();
		msg.put("type", "ALL_ROUNDS_COMPLETE");
		msg.put("finalScores", finalScores);
		msg.put("winner", winner);
		msg.put("timestamp", LocalDateTime.now().toString());
		messagingTemplate.convertAndSend("/topic/room/" + roomCode, (Object) msg);
	}

	private void finalizeSession(Session session, String roomCode) {
		try {
			session.setStatus(SessionStatus.FINISHED);
			session.setEndedAt(LocalDateTime.now());
			sessionRepository.save(session);

			List<Room> rooms = roomRepository.findByRoomCode(roomCode);
			if (!rooms.isEmpty()) {
				Room room = rooms.get(0);
				room.setStatus(RoomStatus.FINISHED);
				room.setClosedAt(LocalDateTime.now());
				roomRepository.save(room);
			}
			log.info("Session {} and room {} finalized after all rounds complete", session.getSessionId(), roomCode);
		} catch (Exception e) {
			log.error("Error finalizing session {}: {}", session.getSessionId(), e.getMessage(), e);
		}
	}

	private void sendPrivateError(String email, String errorMessage) {
		Map<String, Object> msg = new HashMap<>();
		msg.put("type", "GUESS_BLOCKED");
		msg.put("message", errorMessage);
		messagingTemplate.convertAndSendToUser(email, "/queue/errors", msg);
	}

	private void scheduleRetry(Long sessionId, String roomCode, int retryCount) {
		ScheduledFuture<?> task = scheduler.schedule(() -> {
			try {
				startNextRound(sessionId, roomCode, retryCount);
			} catch (Exception e) {
				log.error("Error retrying startNextRound for session {}: {}", sessionId, e.getMessage(), e);
			}
		}, RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS);
		pendingNextRoundTasks.put(sessionId, task);
	}

	public void cleanup(Long sessionId) {
		RoundState round = activeRounds.remove(sessionId);
		if (round != null && round.getTimerTask() != null && !round.getTimerTask().isDone()) {
			round.getTimerTask().cancel(false);
		}
		ScheduledFuture<?> pendingTask = pendingNextRoundTasks.remove(sessionId);
		if (pendingTask != null && !pendingTask.isDone()) {
			pendingTask.cancel(false);
		}
		drawerOrders.remove(sessionId);
		drawerRotationCounters.remove(sessionId);
		roomCodeToSessionId.entrySet().removeIf(e -> e.getValue().equals(sessionId));
	}
}