package com.project.drawguess.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.project.drawguess.enums.RoomStatus;
import com.project.drawguess.model.Room;
import com.project.drawguess.model.RoomPlayer;
import com.project.drawguess.repository.RoomPlayerRepository;
import com.project.drawguess.repository.RoomRepository;
import com.project.drawguess.service.RoomCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Closes WAITING rooms that are abandoned.
 *
 * A room is considered abandoned when it is older than 30 seconds and either:
 * - has zero active players, OR
 * - all active players still have a placeholder WS session (never connected)
 *
 * This handles the edge case where a user creates a room but never navigates
 * to the room page (or navigates away before the WebSocket handshake).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoomCleanupScheduler {

    private final RoomRepository roomRepository;
    private final RoomPlayerRepository roomPlayerRepository;
    private final RoomCacheService roomCacheService;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void closeAbandonedWaitingRooms() {
        List<Room> waitingRooms = roomRepository.findByStatus(RoomStatus.WAITING);
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(30);

        for (Room room : waitingRooms) {
            if (room.getCreatedAt().isAfter(cutoff)) {
                continue;
            }

            List<RoomPlayer> activePlayers = roomPlayerRepository.findByRoomAndIsActive(room, true);
            boolean abandoned = activePlayers.isEmpty()
                    || activePlayers.stream().allMatch(
                            rp -> rp.getWebsocketSessionId().startsWith("pending-"));

            if (abandoned) {
                // Deactivate any lingering placeholder players
                for (RoomPlayer rp : activePlayers) {
                    rp.setIsActive(false);
                    rp.setLeftAt(LocalDateTime.now());
                    roomPlayerRepository.save(rp);
                }
                room.setStatus(RoomStatus.FINISHED);
                room.setClosedAt(LocalDateTime.now());
                roomCacheService.save(room);
                messagingTemplate.convertAndSend("/topic/public-rooms",
                        (Object) Map.of("type", "PUBLIC_ROOMS_UPDATED"));
                log.info("Cleanup: closed abandoned room {} (created {})",
                        room.getRoomCode(), room.getCreatedAt());
            }
        }
    }
}
