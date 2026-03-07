package com.project.drawguess.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.drawguess.dto.RoundSummaryDto;
import com.project.drawguess.model.RoundRecord;
import com.project.drawguess.model.UserSession;
import com.project.drawguess.repository.RoundRecordRepository;
import com.project.drawguess.repository.UserSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
@Slf4j
public class MatchHistoryController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UserSessionRepository userSessionRepository;
    private final RoundRecordRepository roundRecordRepository;

    /**
     * Returns all past matches for the authenticated user, ordered newest first.
     * Canvas strokes are NOT included here — use /round/{id}/canvas for those.
     */
    @GetMapping("/matches")
    public ResponseEntity<?> getMatchHistory(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        String email = principal.getName(); // principal name is email in this app
        List<UserSession> userSessions = userSessionRepository.findByEmailWithSession(email);

        if (userSessions.isEmpty()) {
            return ResponseEntity.ok(Map.of("matches", List.of()));
        }

        List<Long> sessionIds = userSessions.stream()
                .map(us -> us.getSession().getSessionId())
                .distinct()
                .collect(Collectors.toList());

        List<RoundSummaryDto> roundSummaries = roundRecordRepository.findSummariesBySessionIds(sessionIds);

        // Group round summaries by sessionId
        Map<Long, List<RoundSummaryDto>> roundsBySession = roundSummaries.stream()
                .collect(Collectors.groupingBy(RoundSummaryDto::getSessionId));

        // Build match list — one entry per UserSession (a user could have multiple rows
        // per session if they left and rejoined, so deduplicate by sessionId)
        Map<Long, Map<String, Object>> matchMap = new LinkedHashMap<>();

        for (UserSession us : userSessions) {
            Long sessionId = us.getSession().getSessionId();
            if (matchMap.containsKey(sessionId)) continue; // already added

            Map<String, Object> match = new HashMap<>();
            match.put("sessionId", sessionId);
            match.put("roomCode", us.getSession().getRoom().getRoomCode());
            match.put("hostUsername", us.getSession().getRoom().getHost().getUsername());
            match.put("playedAt", us.getSession().getStartedAt());
            match.put("totalRounds", us.getSession().getTotalRounds());
            match.put("status", us.getSession().getStatus() != null ? us.getSession().getStatus().name() : "COMPLETED");
            match.put("myScore", us.getScore());

            // All players in this session — needed for winner + scores
            List<UserSession> sessionPlayers = userSessionRepository.findBySession(us.getSession());
            List<Map<String, Object>> players = sessionPlayers.stream()
                    .sorted(Comparator.comparingInt(UserSession::getScore).reversed())
                    .map(p -> {
                        Map<String, Object> pd = new HashMap<>();
                        pd.put("username", p.getUser().getUsername());
                        pd.put("score", p.getScore());
                        return pd;
                    })
                    .collect(Collectors.toList());
            match.put("players", players);
            // Only declare a winner if there's at least one player with a score > 0
            String winner = null;
            if (!players.isEmpty()) {
                Integer topScore = (Integer) players.get(0).get("score");
                if (topScore != null && topScore > 0) {
                    winner = (String) players.get(0).get("username");
                }
            }
            match.put("winner", winner);

            // Rounds for this session
            List<Map<String, Object>> rounds = new ArrayList<>();
            List<RoundSummaryDto> sessionRounds = roundsBySession.getOrDefault(sessionId, List.of());
            for (RoundSummaryDto r : sessionRounds) {
                Map<String, Object> rd = new HashMap<>();
                rd.put("roundRecordId", r.getRoundRecordId());
                rd.put("roundNumber", r.getRoundNumber());
                rd.put("word", r.getWord());
                rd.put("drawerUsername", r.getDrawerUsername());
                rd.put("endReason", r.getEndReason());
                rd.put("createdAt", r.getCreatedAt());
                rd.put("correctGuessers", parseJsonList(r.getCorrectGuessersJson()));
                rounds.add(rd);
            }
            match.put("rounds", rounds);

            matchMap.put(sessionId, match);
        }

        return ResponseEntity.ok(Map.of("matches", new ArrayList<>(matchMap.values())));
    }

    /**
     * Returns the canvas strokes for a specific round so the frontend can
     * render and download the drawing.
     */
    @GetMapping("/round/{roundRecordId}/canvas")
    public ResponseEntity<?> getRoundCanvas(@PathVariable Long roundRecordId, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        RoundRecord record = roundRecordRepository.findById(roundRecordId).orElse(null);
        if (record == null) return ResponseEntity.notFound().build();

        List<?> strokes = parseJsonList(record.getCanvasStrokesJson());
        return ResponseEntity.ok(Map.of(
                "roundRecordId", roundRecordId,
                "word", record.getWord(),
                "drawerUsername", record.getDrawerUsername(),
                "strokes", strokes
        ));
    }

    @SuppressWarnings("unchecked")
    private List<Object> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON list: {}", e.getMessage());
            return List.of();
        }
    }
}
