package com.project.drawguess.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.project.drawguess.enums.RoomStatus;
import com.project.drawguess.enums.SessionStatus;
import com.project.drawguess.model.Room;
import com.project.drawguess.model.RoomPlayer;
import com.project.drawguess.model.Session;
import com.project.drawguess.model.User;
import com.project.drawguess.repository.RoomPlayerRepository;
import com.project.drawguess.repository.RoomRepository;
import com.project.drawguess.repository.SessionRepository;
import com.project.drawguess.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoomServiceImpl {

	private final RoomRepository roomRepository;
	private final RoomPlayerRepository roomPlayerRepository;
	private final UserRepository userRepository;
	private final SessionRepository sessionRepository;
	private final SessionServiceImpl sessionServiceImpl;
	private final SimpMessagingTemplate messagingTemplate;

	private final Map<String, ScheduledFuture<?>> pendingDisconnectTasks = new ConcurrentHashMap<>();
	private final Map<String, String> disconnectingPlayers = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

	@Value("${app.disconnect.grace-period-seconds:30}")
	private int gracePeriodSeconds;

	@Value("${app.room.max-players:5}")
	private int maxPlayersPerRoom;

	@Transactional
	public Room createRoom(String username) {
		User user = userRepository.findByEmail(username);
		if (user == null) {
			throw new IllegalArgumentException("User " + username + " not found");
		}
		String roomCode = generateRoomCode();
		Room room = new Room(roomCode, user);
		return roomRepository.save(room);
	}

	@Transactional
	public void joinRoomViaWebSocket(String roomCode, String username, String wsSessionId) {

		Room room = roomRepository.findByRoomCode(roomCode).getFirst();
		User user = userRepository.findByEmail(username);

		if (room == null) {
			throw new IllegalArgumentException("Room not found");
		}
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		if (room.getStatus() == RoomStatus.FINISHED) {
			throw new IllegalArgumentException("Cannot join finished room");
		}

		// Check room capacity for new players (reconnecting players bypass the limit)
		long activePlayerCount = roomPlayerRepository.countByRoomAndIsActive(room, true);
		boolean hasExistingRecord = roomPlayerRepository.findByRoomAndUser(room, user).stream().findFirst().isPresent();
		if (activePlayerCount >= maxPlayersPerRoom && !hasExistingRecord) {
			throw new IllegalArgumentException("Room is full (max " + maxPlayersPerRoom + " players)");
		}

		String playerKey = user.getUserId() + ":" + room.getRoomId();
		String oldWsSessionId = disconnectingPlayers.get(playerKey);
		boolean isReconnecting = oldWsSessionId != null;

		if (!isReconnecting) {
			Optional<RoomPlayer> existingRecord = roomPlayerRepository.findByRoomAndUser(room, user).stream()
					.findFirst();
			if (existingRecord.isPresent() ) {
				if(existingRecord.get().getIsActive())
				{
					isReconnecting = true;
					log.info("User {} has existing RoomPlayer record - treating as reconnection", user.getUsername());
				}

			}
		}
		if (room.getStatus() == RoomStatus.PLAYING && !isReconnecting) {
			log.warn("User {} tried to join PLAYING room {} - not a reconnection", user.getUsername(), roomCode);
			messagingTemplate.convertAndSendToUser(username, "/queue/errors", "CANNOT_JOIN_ROOM");
			throw new IllegalArgumentException("Cannot join room - game in progress");
		}

		if (oldWsSessionId != null) {
			ScheduledFuture<?> pendingTask = pendingDisconnectTasks.get(oldWsSessionId);
			if (pendingTask != null && !pendingTask.isDone()) {
				pendingTask.cancel(false);
				log.info("Cancelled pending disconnect for user {} in room {}", user.getUsername(), roomCode);
			}

			pendingDisconnectTasks.remove(oldWsSessionId);
			disconnectingPlayers.remove(playerKey);

			Optional<RoomPlayer> existing = roomPlayerRepository.findByRoomAndIsActive(room, true).stream()
					.filter(roomplayer -> roomplayer.getUser().getUserId().equals(user.getUserId())).findFirst();

			if (existing.isEmpty()) {
				existing = roomPlayerRepository.findByRoomAndUser(room, user).stream().filter(rp -> !rp.getIsActive())
						.findFirst();

				if (existing.isPresent()) {
					RoomPlayer player = existing.get();
					player.setIsActive(true);
					player.setLeftAt(null);
					player.setWebsocketSessionId(wsSessionId);
					roomPlayerRepository.save(player);
					log.info("User {} REACTIVATED in room {}", user.getUsername(), roomCode);
				} else {
					RoomPlayer player = new RoomPlayer(room, user, wsSessionId);
					roomPlayerRepository.save(player);
					log.info("User {} created new entry in room {}", user.getUsername(), roomCode);
				}
			} else {
				RoomPlayer player = existing.get();
				player.setWebsocketSessionId(wsSessionId);
				roomPlayerRepository.save(player);
				log.info("User {} updated ws session id in room {}", user.getUsername(), roomCode);
			}

			String eventType = room.getStatus() == RoomStatus.PLAYING ? "PLAYER_RECONNECTED_SESSION"
					: "PLAYER_RECONNECTED";
			broadcastPlayerUpdate(roomCode, eventType, user.getUsername());

			if (room.getStatus() == RoomStatus.PLAYING) {
				sessionServiceImpl.handlePlayerReconnection(room, user);
			}
		} else {
			Optional<RoomPlayer> existing = roomPlayerRepository.findByRoomAndIsActive(room, true).stream()
					.filter(roomplayer -> roomplayer.getUser().getUserId().equals(user.getUserId())).findFirst();

			if (existing.isPresent()) {
				RoomPlayer player = existing.get();
				player.setWebsocketSessionId(wsSessionId);
				roomPlayerRepository.save(player);
				log.info("User {} updated websocket session in room {}", user.getUsername(), roomCode);

				if (room.getStatus() == RoomStatus.PLAYING) {
					broadcastPlayerUpdate(roomCode, "PLAYER_RECONNECTED_SESSION", user.getUsername());
					sessionServiceImpl.handlePlayerReconnection(room, user);
				} else {
					broadcastPlayerUpdate(roomCode, "PLAYER_JOINED", user.getUsername());
				}
			} else {
				Optional<RoomPlayer> inactiveRecord = roomPlayerRepository.findByRoomAndUser(room, user).stream()
						.filter(rp -> !rp.getIsActive()).findFirst();

				if (inactiveRecord.isPresent()) {
					RoomPlayer player = inactiveRecord.get();
					player.setIsActive(true);
					player.setLeftAt(null);
					player.setWebsocketSessionId(wsSessionId);
					player.setJoinedAt(LocalDateTime.now());
					roomPlayerRepository.save(player);
					log.info("User {} reactivated inactive record in room {}", user.getUsername(), roomCode);

					if (room.getStatus() == RoomStatus.PLAYING) {
						broadcastPlayerUpdate(roomCode, "PLAYER_RECONNECTED_SESSION", user.getUsername());
						sessionServiceImpl.handlePlayerReconnection(room, user);
					} else {
						broadcastPlayerUpdate(roomCode, "PLAYER_JOINED", user.getUsername());
					}
				} else {
					RoomPlayer player = new RoomPlayer(room, user, wsSessionId);
					roomPlayerRepository.save(player);
					log.info("User {} joined room {} for first time", user.getUsername(), roomCode);
					broadcastPlayerUpdate(roomCode, "PLAYER_JOINED", user.getUsername());
				}
			}
		}
	}

	@Transactional
	public void handlePlayerDisconnect(String wsSessionId) {
		if (wsSessionId == null) {
			log.error("handlePlayerDisconnect called with null wsSessionId");
			return;
		}

		Optional<RoomPlayer> playerOptional = roomPlayerRepository.findByWebsocketSessionId(wsSessionId);

		if (playerOptional.isEmpty()) {
			log.warn("No RoomPlayer found for wsSessionId: {}", wsSessionId);
			return;
		}

		RoomPlayer player = playerOptional.get();
		User user = player.getUser();
		Room room = player.getRoom();

		if (user == null || room == null) {
			log.error("RoomPlayer has null user or room");
			return;
		}

		String username = user.getUsername();
		String roomCode = room.getRoomCode();

		log.info("User {} disconnected from room {} - starting {} second grace period", username, roomCode,
				gracePeriodSeconds);

		String playerKey = user.getUserId() + ":" + room.getRoomId();
		disconnectingPlayers.put(playerKey, wsSessionId);

		Session session = sessionRepository.findByRoomAndStatus(room, SessionStatus.ACTIVE).orElse(null);

		String messageType = room.getStatus() == RoomStatus.PLAYING ? "PLAYER_DISCONNECTED_SESSION"
				: "PLAYER_DISCONNECTED";

		Map<String, Object> disconnectMessage = new HashMap<>();
		disconnectMessage.put("type", messageType);
		disconnectMessage.put("username", username);
		disconnectMessage.put("gracePeriod", gracePeriodSeconds);
		disconnectMessage.put("players", getActivePlayers(roomCode));
		disconnectMessage.put("timestamp", LocalDateTime.now().toString());

		if (session != null) {
			disconnectMessage.put("sessionId", session.getSessionId());
		}

		messagingTemplate.convertAndSend("/topic/room/" + roomCode, (Object) disconnectMessage);

		if (session != null && room.getStatus() == RoomStatus.PLAYING) {
			sessionServiceImpl.handleSessionPlayerDisconnect(wsSessionId, room, user, session);
		}

		ScheduledFuture<?> disconnectTask = scheduler.schedule(() -> handleDelayedPlayerDisconnect(wsSessionId, player),
				gracePeriodSeconds, TimeUnit.SECONDS);

		pendingDisconnectTasks.put(wsSessionId, disconnectTask);

		log.info("Grace Period timer started for {} in room {}", username, roomCode);
	}

	@Transactional
	public void handleDelayedPlayerDisconnect(String wsSession, RoomPlayer player) {
		if (player == null || player.getUser() == null || player.getRoom() == null) {
			log.error("handleDelayedPlayerDisconnect called with null data");
			if (wsSession != null) {
				pendingDisconnectTasks.remove(wsSession);
			}
			return;
		}

		String playerKey = player.getUser().getUserId() + ":" + player.getRoom().getRoomId();

		if (!disconnectingPlayers.containsKey(playerKey)) {
			log.info("Player {} already reconnected, skipping disconnect", player.getUser().getUsername());
			return;
		}
		log.info("Removed playerkey from disconnectingPlayers {}, disconnectingPlayers list -> {}", playerKey, disconnectingPlayers);
		disconnectingPlayers.remove(playerKey);
		pendingDisconnectTasks.remove(wsSession);

		String username = player.getUser().getUsername();
		String roomCode = player.getRoom().getRoomCode();

		log.info("Grace period expired for {} in room {} - removing user", username, roomCode);

		player.setIsActive(false);
		player.setLeftAt(LocalDateTime.now());
		roomPlayerRepository.save(player);

		log.info("User {} left room {} (grace period done)", username, roomCode);

		sessionServiceImpl.handlePlayerLeaveSession(wsSession);
		broadcastPlayerUpdate(roomCode, "PLAYER_LEFT", username);

		long activeCount = roomPlayerRepository.countByRoomAndIsActive(player.getRoom(), true);
		
		
		if(activeCount < 1 && player.getRoom().getStatus() == RoomStatus.WAITING)
		{
			if(disconnectingPlayers.isEmpty())
			{
				Room room = player.getRoom();
				room.setStatus(RoomStatus.FINISHED);
				room.setClosedAt(LocalDateTime.now());
				roomRepository.save(room);
				log.info("Room {} closed - no session started, all players left ", roomCode);
			}
		}
		
		if(activeCount < 2 && player.getRoom().getStatus() != RoomStatus.WAITING)
		{
			Room room = player.getRoom();
			room.setStatus(RoomStatus.FINISHED);
			room.setClosedAt(LocalDateTime.now());
			roomRepository.save(room);
			sessionServiceImpl.endSession(roomCode);
			log.info("Room {} closed - empty", roomCode);
		}
		
		
		if (activeCount == 0) {
			Room room = player.getRoom();
			room.setStatus(RoomStatus.FINISHED);
			room.setClosedAt(LocalDateTime.now());
			roomRepository.save(room);
			log.info("Room {} closed - empty", roomCode);
		} else {
			reassignHostIfNeeded(player.getRoom(), player.getUser());
		}
	}

	private void reassignHostIfNeeded(Room room, User disconnectedUser) {
		if (!room.getHost().getUserId().equals(disconnectedUser.getUserId())) {
			return;
		}

		List<RoomPlayer> activePlayers = roomPlayerRepository.findByRoomAndIsActive(room, true);

		if (activePlayers.isEmpty()) {
			log.info("No active players to reassign host in room {}", room.getRoomCode());
			return;
		}

		User newHost = activePlayers.get(0).getUser();
		room.setHost(newHost);
		roomRepository.save(room);
		log.info("Host reassigned to {} in room {}", newHost.getUsername(), room.getRoomCode());

		Map<String, Object> hostChangeMessage = new HashMap<>();
		hostChangeMessage.put("type", "HOST_CHANGED");
		hostChangeMessage.put("newHost", newHost.getUsername());
		hostChangeMessage.put("players", getActivePlayers(room.getRoomCode()));
		hostChangeMessage.put("timestamp", LocalDateTime.now().toString());

		messagingTemplate.convertAndSend("/topic/room/" + room.getRoomCode(), (Object) hostChangeMessage);
	}

	@Transactional()
	public List<Map<String, Object>> getActivePlayers(String roomCode) {
		Room room = roomRepository.findByRoomCode(roomCode).getFirst();

		List<RoomPlayer> players = roomPlayerRepository.findByRoomAndIsActive(room, true);

		return players.stream().map(player -> {
			Map<String, Object> playerData = new HashMap<>();
			playerData.put("userId", player.getUser().getUserId());
			playerData.put("username", player.getUser().getUsername());
			playerData.put("isHost", room.getHost().getUserId().equals(player.getUser().getUserId()));
			playerData.put("joinedAt", player.getJoinedAt().toString());
			return playerData;
		}).collect(Collectors.toList());
	}

	private void broadcastPlayerUpdate(String roomCode, String eventType, String username) {
		Map<String, Object> message = new HashMap<>();
		message.put("type", eventType);
		message.put("username", username);
		message.put("players", getActivePlayers(roomCode));
		message.put("timestamp", LocalDateTime.now().toString());

		messagingTemplate.convertAndSend("/topic/room/" + roomCode, (Object) message);
	}

	public String generateRoomCode() {
		String code;
		do {
			code = String.format("%06d", new Random().nextInt(1000000));
		} while (roomRepository.existsByRoomCode(code));
		return code;
	}

	public Room getRoomByCode(String roomCode) {
		Room room = roomRepository.findByRoomCode(roomCode).getFirst();
		if (room == null) {
			throw new IllegalArgumentException("Room not found Exception");
		}
		return room;
	}
}