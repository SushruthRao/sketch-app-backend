package com.project.drawguess.controller;

import com.project.drawguess.dto.AdminRoomDto;
import com.project.drawguess.dto.AdminStatsDto;
import com.project.drawguess.dto.AdminUserDto;
import com.project.drawguess.dto.PagedResponse;
import com.project.drawguess.enums.RoomStatus;
import com.project.drawguess.enums.SessionStatus;
import com.project.drawguess.model.Room;
import com.project.drawguess.model.Session;
import com.project.drawguess.model.User;
import com.project.drawguess.repository.RoomPlayerRepository;
import com.project.drawguess.repository.RoomRepository;
import com.project.drawguess.repository.SessionRepository;
import com.project.drawguess.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final SessionRepository sessionRepository;
    private final RoomPlayerRepository roomPlayerRepository;

    // Holds open SSE connections for the live stats stream
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // ─── Stats ────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDto> getStats() {
        return ResponseEntity.ok(buildStats());
    }

    // Keeps an SSE connection open and pushes fresh stats every 5 seconds.
    // The frontend just reads this stream — no polling needed on its side.
    @GetMapping(value = "/stats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStats() {
        SseEmitter emitter = new SseEmitter(0L); // 0 = no timeout
        emitters.add(emitter);

        // Clean up the list when the client disconnects or times out
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        // Send the current snapshot immediately so the UI doesn't sit blank
        try {
            emitter.send(SseEmitter.event().data(buildStats(), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    // Runs every 5 seconds and pushes updated stats to all connected clients.
    // Dead emitters (closed tabs, network drops) are pruned each cycle.
    @Scheduled(fixedDelay = 5000)
    public void pushStats() {
        if (emitters.isEmpty()) return;

        AdminStatsDto stats = buildStats();
        List<SseEmitter> dead = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(stats, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }

        emitters.removeAll(dead);
    }

    // ─── Rooms ────────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of rooms, optionally filtered by status.
     *
     * Query params:
     *   page   — 0-based page index (default 0)
     *   size   — page size, capped at 50 (default 10)
     *   status — comma-separated RoomStatus values, e.g. "WAITING,PLAYING"
     *            omit to get all rooms regardless of status
     */
    @GetMapping("/rooms")
    public ResponseEntity<PagedResponse<AdminRoomDto>> getRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {

        // Cap page size so someone can't accidentally request the entire table
        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, size);

        Page<Room> roomPage;
        if (status != null && !status.isBlank()) {
            List<RoomStatus> statuses = Arrays.stream(status.split(","))
                    .map(String::trim)
                    .map(RoomStatus::valueOf)
                    .collect(Collectors.toList());
            roomPage = roomRepository.findByStatusInOrderByCreatedAtDesc(statuses, pageable);
        } else {
            roomPage = roomRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<AdminRoomDto> dtos = roomPage.getContent().stream()
                .map(this::toRoomDto)
                .collect(Collectors.toList());

        PagedResponse<AdminRoomDto> response = new PagedResponse<>(
                dtos,
                roomPage.getNumber(),
                roomPage.getSize(),
                roomPage.getTotalElements(),
                roomPage.getTotalPages(),
                roomPage.isLast()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Force-closes a room — sets it to FINISHED and terminates any active session.
     * Use this for moderation (e.g. abusive room names, stuck games).
     * Does not disconnect WebSocket sessions; players will see the game end naturally
     * on their next interaction or when the grace period fires.
     */
    @DeleteMapping("/rooms/{roomCode}")
    public ResponseEntity<Void> forceCloseRoom(@PathVariable String roomCode) {
        // Find the most recent non-FINISHED room with this code
        Room room = roomRepository
                .findFirstByRoomCodeAndStatusNot(roomCode, RoomStatus.FINISHED)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No active room with code " + roomCode));

        // If there's a live session, close it too
        Optional<Session> activeSession = sessionRepository.findByRoomAndStatus(room, SessionStatus.ACTIVE);
        activeSession.ifPresent(s -> {
            s.setStatus(SessionStatus.FINISHED);
            s.setEndedAt(LocalDateTime.now());
            sessionRepository.save(s);
        });

        // Mark room as finished
        room.setStatus(RoomStatus.FINISHED);
        room.setClosedAt(LocalDateTime.now());
        roomRepository.save(room);

        log.info("Admin force-closed room {}", roomCode);
        return ResponseEntity.noContent().build();
    }

    // ─── Users ────────────────────────────────────────────────────────────────

    /**
     * Paginated user search. Matches against username and email (case-insensitive).
     * An empty query returns all users newest-first.
     *
     * Query params:
     *   q    — search string (default "")
     *   page — 0-based page index (default 0)
     *   size — page size, capped at 50 (default 10)
     */
    @GetMapping("/users/search")
    public ResponseEntity<PagedResponse<AdminUserDto>> searchUsers(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, size);

        // Spring Data matches the same term against both username and email
        Page<User> userPage = userRepository
                .findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q, pageable);

        List<AdminUserDto> dtos = userPage.getContent().stream()
                .map(u -> new AdminUserDto(
                        u.getUserId(),
                        u.getUsername(),
                        u.getEmail(),
                        u.isAdmin(),
                        u.getCreatedAt()))
                .collect(Collectors.toList());

        PagedResponse<AdminUserDto> response = new PagedResponse<>(
                dtos,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.isLast()
        );

        return ResponseEntity.ok(response);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private AdminStatsDto buildStats() {
        // "Today" spans from midnight to the current moment
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);
        LocalDateTime now = LocalDateTime.now();

        long totalUsers        = userRepository.count();
        long newUsersToday     = userRepository.countByCreatedAtBetween(startOfDay, now);
        long waitingRooms      = roomRepository.findByStatus(RoomStatus.WAITING).size();
        long playingRooms      = roomRepository.findByStatus(RoomStatus.PLAYING).size();
        long finishedRooms     = roomRepository.findByStatus(RoomStatus.FINISHED).size();
        long totalRooms        = roomRepository.count();
        long activeSessions    = sessionRepository.countByStatus(SessionStatus.ACTIVE);
        long totalSessions     = sessionRepository.count();
        long totalRoundsPlayed = sessionRepository.sumAllCurrentRounds();
        long activePlayers     = roomPlayerRepository.countByIsActive(true);

        return new AdminStatsDto(totalUsers, newUsersToday, waitingRooms, playingRooms,
                finishedRooms, totalRooms, activeSessions, totalSessions,
                totalRoundsPlayed, activePlayers);
    }

    // Converts a Room entity into the lightweight DTO sent to the frontend.
    // Player count is queried separately because Room doesn't hold a live player list.
    private AdminRoomDto toRoomDto(Room room) {
        long playerCount = roomPlayerRepository.countByRoomAndIsActive(room, true);

        // Pull round info from the active session if one exists, otherwise default to 0
        int currentRound = 0;
        int totalRounds  = 0;
        Optional<Session> session = sessionRepository.findActiveSessionByRoomId(room.getRoomId());
        if (session.isPresent()) {
            currentRound = session.get().getCurrentRound();
            totalRounds  = session.get().getTotalRounds();
        }

        return new AdminRoomDto(
                room.getRoomCode(),
                room.getHost().getUsername(),
                room.getStatus().name(),
                playerCount,
                currentRound,
                totalRounds,
                room.getIsPublic(),
                room.getCreatedAt()
        );
    }
}
