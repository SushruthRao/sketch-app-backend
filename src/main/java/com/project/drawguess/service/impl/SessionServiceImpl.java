package com.project.drawguess.service.impl;

import java.net.http.WebSocket;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.project.drawguess.enums.RoomStatus;
import com.project.drawguess.enums.SessionStatus;
import com.project.drawguess.game.GameRoundManager;
import com.project.drawguess.model.Room;
import java.util.ArrayList;
import com.project.drawguess.model.RoomPlayer;
import com.project.drawguess.model.Session;
import com.project.drawguess.model.User;
import com.project.drawguess.model.UserSession;
import com.project.drawguess.repository.RoomPlayerRepository;
import com.project.drawguess.repository.RoomRepository;
import com.project.drawguess.repository.SessionRepository;
import com.project.drawguess.repository.UserRepository;
import com.project.drawguess.repository.UserSessionRepository;

import org.springframework.beans.factory.annotation.Value;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SessionServiceImpl {
	private final SessionRepository sessionRepository;
	private final UserSessionRepository userSessionRepository;
	private final RoomRepository roomRepository;
	private final RoomPlayerRepository roomPlayerRepository;
	private final UserRepository userRepository;
	private final SimpMessagingTemplate messagingTemplate;
	private final GameRoundManager gameRoundManager;
	private final CanvasStrokeService canvasStrokeService;

	private final Map<String, ScheduledFuture<?>> sessionDisconnectTasks = new ConcurrentHashMap<>();
	private final Map<String, String> disconnectingSessionPlayers = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

	@Value("${app.disconnect.grace-period-seconds:30}")
	private int gracePeriodSeconds;

	@Transactional
	public Session startSession(String roomCode, String hostEmail) {
		Room room = roomRepository.findByRoomCode(roomCode).getFirst();
		User host = userRepository.findByEmail(hostEmail);

		if (!room.getHost().getUserId().equals(host.getUserId())) {
			throw new IllegalStateException("Only host can start the session");
		}
		if (room.getStatus() != RoomStatus.WAITING) {
			throw new IllegalStateException("Session is already in progress");
		}
		// Check no active session already exists (guards against concurrent start requests)
		Optional<Session> existingSession = sessionRepository.findByRoomAndStatus(room, SessionStatus.ACTIVE);
		if (existingSession.isPresent()) {
			throw new IllegalStateException("Session is already in progress");
		}

		List<RoomPlayer> activePlayers = roomPlayerRepository.findByRoomAndIsActive(room, true);

		// Deduplicate by user ID to prevent duplicate UserSessions
		activePlayers = new ArrayList<>(activePlayers.stream()
				.collect(Collectors.toMap(
						rp -> rp.getUser().getUserId(),
						rp -> rp,
						(existing, duplicate) -> existing,
						java.util.LinkedHashMap::new))
				.values());

		if (activePlayers.size() < 2) {
			throw new IllegalStateException("Need at least 2 users to start session");
		}
		room.setStatus(RoomStatus.PLAYING);
		roomRepository.save(room);

		Session session = new Session(room, activePlayers.size() * 2);
		session.setStatus(SessionStatus.ACTIVE);
		session = sessionRepository.save(session);
		log.info("Session created for room : {}", roomCode);

		for (RoomPlayer rp : activePlayers) {
			boolean isHost = rp.getUser().getUserId().equals(host.getUserId());
			UserSession userSession = new UserSession(rp.getUser(), session, isHost);
			userSessionRepository.save(userSession);
			log.info("UserSession created for user : {}", rp.getUser().getUsername());

		}
		broadcastGameStarted(roomCode, session);
		gameRoundManager.initializeGame(session, roomCode);
		return session;

	}

	@Transactional
	public void endSession(String roomCode) {
		List<Room> rooms = roomRepository.findByRoomCode(roomCode);
		if (rooms.isEmpty()) {
			log.warn("endSession: Room not found for code {}", roomCode);
			return;
		}
		Room room = rooms.get(0);
		Optional<Session> sessionOpt = sessionRepository.findByRoomAndStatus(room, SessionStatus.ACTIVE);
		if (sessionOpt.isEmpty()) {
			log.info("endSession: No active session for room {} (already ended?)", roomCode);
			return;
		}
		Session session = sessionOpt.get();
		session.setStatus(SessionStatus.FINISHED);
		session.setEndedAt(LocalDateTime.now());
		sessionRepository.save(session);
		if (room.getStatus() != RoomStatus.FINISHED) {
			room.setStatus(RoomStatus.FINISHED);
			room.setClosedAt(LocalDateTime.now());
			roomRepository.save(room);
		}
		log.info("Session ended : {} for room {} ", session.getSessionId(), roomCode);

		gameRoundManager.cleanup(session.getSessionId());

		sessionDisconnectTasks.forEach((wsId, task) -> {
			if (task != null && !task.isDone()) {
				task.cancel(false);
			}
		});
		sessionDisconnectTasks.clear();
		disconnectingSessionPlayers.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + session.getSessionId()));

		broadcastGameEnded(roomCode, session);

	}

	@Transactional()
	public List<Map<String, Object>> getSessionPlayers(Long sessionId) {
		List<UserSession> userSessions = userSessionRepository.findActiveUsersBySessionId(sessionId);
		if(userSessions.isEmpty()) throw new IllegalStateException("No session players found");
		return userSessions.stream().map(us -> {
			Map<String, Object> playerData = new HashMap<>();
			playerData.put("userId", us.getUser().getUserId());
			playerData.put("username", us.getUser().getUsername());
			playerData.put("score", us.getScore());
			playerData.put("isHost", us.getIsHost());
			return playerData;
		}).collect(Collectors.toList());
	}

	@Transactional
	public void handleSessionPlayerDisconnect(String wsSessionId, Room room, User user, Session session) {
		if (session == null || session.getStatus() != SessionStatus.ACTIVE) {
			log.info("No active session for disconnect handling");
			return;
		}

		String username = user.getUsername();
		String roomCode = room.getRoomCode();
		String playerKey = user.getUserId() + ":" + session.getSessionId();

		log.info("User {} disconnected from SESSION {} - starting {} second grace period", username,
				session.getSessionId(), gracePeriodSeconds);

		disconnectingSessionPlayers.put(playerKey, wsSessionId);


		ScheduledFuture<?> task = scheduler.schedule(
				() -> handleDelayedSessionDisconnect(wsSessionId, room, user, session), gracePeriodSeconds,
				TimeUnit.SECONDS);

		sessionDisconnectTasks.put(wsSessionId, task);
		log.info("Session grace period timer started for {} in session {}", username, session.getSessionId());
	}

	@Transactional
	public void handleDelayedSessionDisconnect(String wsSessionId, Room room, User user, Session session) {
		String playerKey = user.getUserId() + ":" + session.getSessionId();

		if (!disconnectingSessionPlayers.containsKey(playerKey)) {
			log.info("Player {} already reconnected to session, skipping disconnect", user.getUsername());
			return;
		}

		disconnectingSessionPlayers.remove(playerKey);
		sessionDisconnectTasks.remove(wsSessionId);

		log.info("Session grace period expired for {} - marking inactive", user.getUsername());

		Optional<UserSession> userSessionOpt = userSessionRepository.findByUserAndSession(user, session);
		if (userSessionOpt.isPresent()) {
			UserSession userSession = userSessionOpt.get();
			if (userSession.getIsActive()) {
				userSession.setIsActive(false);
				userSession.setLeftAt(LocalDateTime.now());
				userSessionRepository.save(userSession);

				Map<String, Object> message = new HashMap<>();
				message.put("type", "PLAYER_LEFT_SESSION");
				message.put("username", user.getUsername());
				message.put("sessionId", session.getSessionId());
				message.put("timestamp", LocalDateTime.now().toString());
				messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(), (Object) message);
			}
		}

		gameRoundManager.handleDrawerDisconnect(session.getSessionId(), room.getRoomCode(), user.getUserId());
		gameRoundManager.handleGuesserDisconnect(session.getSessionId(), room.getRoomCode());

		long activePlayers = userSessionRepository.countActivePlayersBySessionId(session.getSessionId());
		if (activePlayers < 2) {
			log.warn("Less than 2 players in session {}, ending session", session.getSessionId());
			endSession(room.getRoomCode());
		}
	}

	@Transactional
	public void handlePlayerLeaveSession(String wsSessionId) {
		RoomPlayer roomPlayer = roomPlayerRepository.findByWebsocketSessionId(wsSessionId).orElse(null);

		if (roomPlayer == null || !roomPlayer.getIsActive()) {
			return;
		}

		Room room = roomPlayer.getRoom();
		Session session = sessionRepository.findByRoomAndStatus(room, SessionStatus.ACTIVE).orElse(null);

		if (session == null) {
			return;
		}

		Optional<UserSession> userSessionOpt = userSessionRepository.findByUserAndSession(roomPlayer.getUser(),
				session);

		UserSession userSession = userSessionOpt.get();

		if (userSession != null && userSession.getIsActive()) {
			userSession.setIsActive(false);
			userSession.setLeftAt(LocalDateTime.now());
			userSessionRepository.save(userSession);
			log.info("UserSession inactive for user {} in session {} ", roomPlayer.getUser().getUsername(),
					session.getSessionId());

			Map<String, Object> message = new HashMap<>();
			message.put("type", "PLAYER_LEFT_SESSION");
			message.put("username", roomPlayer.getUser().getUsername());
			message.put("timestamp", LocalDateTime.now().toString());
			messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(), (Object) message);
		}

		long activePlayers = userSessionRepository.countActivePlayersBySessionId(session.getSessionId());

		if (activePlayers < 2) {
			log.warn("Less than 2 players in session, ending session");
			endSession(room.getRoomCode());
		}

	}

	@Transactional
	public void handlePlayerReconnection(Room room, User user) {
		log.info("Handling session reconnection for user {} in room {}", user.getUsername(), room.getRoomCode());
		Session session = sessionRepository.findByRoomAndStatus(room, SessionStatus.ACTIVE).orElse(null);

		if (session == null) {
			log.info("No active session for room {}", room);
			return;
		}

		Optional<UserSession> userSessionOpt = userSessionRepository.findByUserAndSession(user, session);

		if (userSessionOpt.isEmpty()) {
			log.warn("No user session for user {} in room {} ", user.getUsername(), room.getRoomCode());
			return;
		}

		UserSession userSession = userSessionOpt.get();
		if (!userSession.getIsActive()) {
			userSession.setIsActive(true);
			userSession.setLeftAt(null);
			userSessionRepository.save(userSession);
			log.info("reconnected usersession for user {} in session {}", user.getUsername(), session.getSessionId());
		}
		String playerKey = user.getUserId() + ":" + session.getSessionId();
		String wsSessionId = disconnectingSessionPlayers.get(playerKey);
		if (wsSessionId != null) {
			ScheduledFuture<?> task = sessionDisconnectTasks.get(wsSessionId);
			if (task != null && !task.isDone()) {
				task.cancel(false);
				log.info("Cancelled pending session disconnect task for user {} ", user.getUsername());
			}
			sessionDisconnectTasks.remove(wsSessionId);
			disconnectingSessionPlayers.remove(playerKey);
		}

		Map<String, Object> roundState = gameRoundManager.getRoundStateForReconnection(session.getSessionId());
		if (roundState != null) {
			roundState.put("type", "ROUND_STATE");
			messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/round-state", roundState);

			String word = gameRoundManager.getWordForDrawer(session.getSessionId(), user.getUserId());
			if (word != null) {
				Map<String, Object> wordMsg = new HashMap<>();
				wordMsg.put("type", "YOUR_WORD");
				wordMsg.put("word", word);
				wordMsg.put("roundNumber", roundState.get("roundNumber"));
				messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/word", wordMsg);
			}
		} else {
			Map<String, Object> betweenRoundsState = gameRoundManager.getBetweenRoundsState(session.getSessionId());
			if (betweenRoundsState != null) {
				betweenRoundsState.put("type", "ROUND_STATE");
				messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/round-state", betweenRoundsState);
			}
		}

		// Send canvas stroke history for reconnection replay
		List<Map<String, Object>> strokes = canvasStrokeService.getStrokes(room.getRoomCode());
		if (strokes != null && !strokes.isEmpty()) {
			Map<String, Object> canvasState = new HashMap<>();
			canvasState.put("type", "CANVAS_STATE");
			canvasState.put("strokes", new ArrayList<>(strokes));
			messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/canvas-state", canvasState);
		}
	}

	private void broadcastGameStarted(String roomCode, Session session) {
		Map<String, Object> message = new HashMap<>();
		message.put("type", "GAME_STARTED");
		message.put("sessionId", session.getSessionId());
		message.put("totalRounds", session.getTotalRounds());
		message.put("currentRound", session.getCurrentRound());
		message.put("players", getSessionPlayers(session.getSessionId()));
		message.put("timestamp", LocalDateTime.now().toString());

		messagingTemplate.convertAndSend("/topic/room/" + roomCode, (Object) message);
		log.info("Session started at {}", roomCode);
	}

	private void broadcastGameEnded(String roomCode, Session session) {
		log.info("Broadcast Game ended called for roomCode - {}" , roomCode);
		List<UserSession> userSessions = userSessionRepository.findBySession(session);

		List<Map<String, Object>> finalScores = userSessions.stream().map(us -> {
			Map<String, Object> scoreData = new HashMap<>();
			scoreData.put("username", us.getUser().getUsername());
			scoreData.put("score", us.getScore());
			return scoreData;
		}).sorted((a, b) -> Integer.compare((Integer) b.get("score"), (Integer) a.get("score")))
				.collect(Collectors.toList());
		String winner = finalScores.isEmpty() ? null : (String) finalScores.get(0).get("username");
		Map<String, Object> message = new HashMap<>();
		message.put("type", "GAME_ENDED");
		message.put("finalScores", finalScores);
		message.put("winner", winner);
		messagingTemplate.convertAndSend("/topic/room/" + roomCode, (Object) message);
		log.info("Sent GAME_ENDED to /topic/room");
	}

	@Transactional()
	public Session getActiveSession(String roomCode) {
		Room room = roomRepository.findByRoomCode(roomCode).getFirst();
		return sessionRepository.findByRoomAndStatus(room, SessionStatus.ACTIVE).orElse(null);
	}

}
