package com.project.drawguess.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import com.project.drawguess.game.GameRoundManager;
import com.project.drawguess.model.Session;
import com.project.drawguess.service.impl.CanvasStrokeServiceImpl;
import com.project.drawguess.service.impl.SessionServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class BinaryCanvasHandler extends AbstractWebSocketHandler {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final CanvasStrokeServiceImpl canvasStrokeService;
    private final GameRoundManager gameRoundManager;
    private final SessionServiceImpl sessionServiceImpl;

    // roomCode → { wsSessionId → WebSocketSession }
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToRoom = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String username = (String) session.getAttributes().get("username");
        String roomCode = (String) session.getAttributes().get("roomCode");

        if (username == null || roomCode == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        sessionToUser.put(session.getId(), username);
        sessionToRoom.put(session.getId(), roomCode);
        roomSessions.computeIfAbsent(roomCode, k -> new ConcurrentHashMap<>())
                    .put(session.getId(), session);

        log.info("Canvas binary WS connected: {} for room {}", username, roomCode);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String username = sessionToUser.get(session.getId());
        String roomCode = sessionToRoom.get(session.getId());
        if (username == null || roomCode == null) return;

        byte[] data = message.getPayload().array();
        if (data.length == 0) return;

        try {
            switch (data[0]) {
                case BinaryCanvasCodec.MSG_STROKE -> handleStroke(data, username, roomCode, session.getId());
                case BinaryCanvasCodec.MSG_CLEAR  -> handleClear(username, roomCode);
                case BinaryCanvasCodec.MSG_REQUEST_STATE -> handleRequestState(session, roomCode);
                default -> log.warn("Unknown canvas msg_type 0x{} from {}", Integer.toHexString(data[0] & 0xFF), username);
            }
        } catch (Exception e) {
            log.error("Error handling canvas binary message from {}: {}", username, e.getMessage());
            sendError(session, e.getMessage());
        }
    }

    private void handleStroke(byte[] data, String username, String roomCode, String senderSessionId) throws IOException {
        Session gameSession = sessionServiceImpl.getActiveSession(roomCode);
        if (gameSession != null && !gameRoundManager.isDrawerForRoom(roomCode, username)) {
            log.warn("Draw rejected for {} in room {} - not the drawer", username, roomCode);
            return;
        }

        Map<String, Object> strokeData = BinaryCanvasCodec.decodeClientStroke(data);
        canvasStrokeService.addStroke(roomCode, strokeData);

        broadcastBinary(roomCode, BinaryCanvasCodec.encodeStroke(strokeData, username), senderSessionId);
    }

    private void handleClear(String username, String roomCode) throws IOException {
        if (!gameRoundManager.isDrawerForRoom(roomCode, username)) return;
        canvasStrokeService.clearStrokes(roomCode);
        broadcastBinary(roomCode, BinaryCanvasCodec.encodeClear(), null);
    }

    private void handleRequestState(WebSocketSession session, String roomCode) throws IOException {
        List<Map<String, Object>> strokes = canvasStrokeService.getStrokes(roomCode);
        if (strokes != null && !strokes.isEmpty()) {
            byte[] stateBytes = BinaryCanvasCodec.encodeCanvasState(new ArrayList<>(strokes));
            session.sendMessage(new BinaryMessage(stateBytes));
            log.info("Sent {} canvas strokes (binary) to {} for room {}",
                    strokes.size(), sessionToUser.get(session.getId()), roomCode);
        }
    }

    private void broadcastBinary(String roomCode, byte[] data, String excludeSessionId) {
        ConcurrentHashMap<String, WebSocketSession> sessions = roomSessions.get(roomCode);
        if (sessions == null) return;

        BinaryMessage msg = new BinaryMessage(data);
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            if (excludeSessionId != null && entry.getKey().equals(excludeSessionId)) continue;
            WebSocketSession ws = entry.getValue();
            if (ws.isOpen()) {
                try {
                    ws.sendMessage(msg);
                } catch (IOException e) {
                    log.error("Failed to send canvas binary to session {}", ws.getId());
                }
            }
        }
    }

    private void sendError(WebSocketSession session, String message) {
        if (!session.isOpen()) return;
        try {
            String json = MAPPER.writeValueAsString(Map.of("type", "CANVAS_ERROR", "message", message != null ? message : "Canvas error"));
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("Failed to send canvas error to session {}", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomCode = sessionToRoom.remove(session.getId());
        String username = sessionToUser.remove(session.getId());
        if (roomCode != null) {
            ConcurrentHashMap<String, WebSocketSession> sessions = roomSessions.get(roomCode);
            if (sessions != null) {
                sessions.remove(session.getId());
                if (sessions.isEmpty()) roomSessions.remove(roomCode);
            }
        }
        log.info("Canvas binary WS disconnected: {} ({})", username, session.getId());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
