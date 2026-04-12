package com.project.drawguess.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.project.drawguess.game.GameRoundManager;
import com.project.drawguess.model.Session;
import com.project.drawguess.model.User;
import com.project.drawguess.repository.UserRepository;
import com.project.drawguess.service.impl.RoomServiceImpl;
import com.project.drawguess.service.impl.SessionServiceImpl;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtendWith(MockitoExtension.class)
class WebSocketControllerTest {

    @Mock private SessionServiceImpl sessionService;
    @Mock private RoomServiceImpl roomService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private UserRepository userRepository;
    @Mock private GameRoundManager gameRoundManager;
    @Mock private Principal principal;
    @Mock private SimpMessageHeaderAccessor headerAccessor;
    
    
    
    @InjectMocks
    private WebSocketController webSocketController;

    @Captor
    private ArgumentCaptor<Map<String, Object>> mapCaptor;

    private final String ROOM_CODE = "123456";
    private final String USERNAME = "TestUser";
    private final String EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        lenient().when(principal.getName()).thenReturn(EMAIL);
    }

    @Test
    @DisplayName("Should successfully join room via WebSocket")
    void joinRoom_Success() {
        log.info("Running: joinRoom_Success - Room: {}, User: {}", ROOM_CODE, EMAIL);
        when(headerAccessor.getSessionId()).thenReturn("ws-001");
        
        webSocketController.joinRoom(ROOM_CODE, headerAccessor, principal);
        
        verify(roomService).joinRoomViaWebSocket(ROOM_CODE, EMAIL, "ws-001");
        log.info("Success: joinRoom_Success");
    }

    @Test
    @DisplayName("Should send structured error to user via /queue/errors")
    void handleWebSocketException_SendsProperError() {
        log.info("Running: handleWebSocketException_SendsProperError");
        String errorMsg = "Simulated Socket Error";
        Exception ex = new RuntimeException(errorMsg);

        webSocketController.handleWebSocketException(ex, principal);

        verify(messagingTemplate).convertAndSendToUser(eq(EMAIL), eq("/queue/errors"), mapCaptor.capture());
        
        Map<String, Object> errorPayload = mapCaptor.getValue();
        log.info("Captured Error Response: {}", errorPayload);
        
        assertThat(errorPayload).containsEntry("message", errorMsg);
    }
    
    @Test
    @DisplayName("Edge Case: Start Game - Principal is Null")
    void startGame_NullPrincipal_ShouldAbort() {
        log.warn("Running: startGame_NullPrincipal_ShouldAbort");
        
        webSocketController.startGame(ROOM_CODE, null);

        // Verify that the service was never called because principal check failed
        verifyNoInteractions(sessionService);
        log.info("Verified: Logic aborted correctly on null principal");
    }

    @Test
    @DisplayName("Edge Case: Send Guess - Payload is Null")
    void sendGuess_NullPayload_ShouldAbort() {
        log.warn("Running: sendGuess_NullPayload_ShouldAbort");
        
        webSocketController.sendGuess(ROOM_CODE, headerAccessor, principal, null);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(messagingTemplate);
        log.info("Verified: Logic aborted correctly on null payload");
    }

    @Test
    @DisplayName("Edge Case: Send Guess - User not found in DB")
    void sendGuess_UserNotFound_ShouldAbort() {
        log.warn("Running: sendGuess_UserNotFound_ShouldAbort");
        Map<String, String> payload = Map.of("message", "Valid Guess");
        
        // Mock DB returning null
        when(userRepository.findByEmail(EMAIL)).thenReturn(null);

        webSocketController.sendGuess(ROOM_CODE, headerAccessor, principal, payload);

        // Verify it stops after DB check
        verifyNoInteractions(sessionService);
        verifyNoInteractions(messagingTemplate);
        log.info("Verified: Logic aborted because user was not found");
    }

    @Test
    @DisplayName("Edge Case: Send Guess - Blank message (spaces only)")
    void sendGuess_BlankMessage_ShouldIgnore() {
        log.warn("Running: sendGuess_BlankMessage_ShouldIgnore");
        Map<String, String> payload = Map.of("message", "    ");

        webSocketController.sendGuess(ROOM_CODE, headerAccessor, principal, payload);

        // Should not reach DB or Messaging logic
        verifyNoInteractions(userRepository);
        log.info("Verified: Blank messages are ignored");
    }

    @Test
    @DisplayName("Edge Case: Send Guess - Principal Null (Guessing)")
    void sendGuess_NullPrincipal_ShouldAbort() {
        log.warn("Running: sendGuess_NullPrincipal_ShouldAbort");
        Map<String, String> payload = Map.of("message", "Guess");

        webSocketController.sendGuess(ROOM_CODE, headerAccessor, null, payload);

        verifyNoInteractions(userRepository);
        log.info("Verified: Null principal blocked from guessing");
    }

    @Test
    @DisplayName("Edge Case: Send Guess - Missing 'message' key in payload")
    void sendGuess_MissingMessageKey_ShouldIgnore() {
        log.warn("Running: sendGuess_MissingMessageKey_ShouldIgnore");
        // Payload exists but doesn't have the key "message"
        Map<String, String> payload = Map.of("wrongKey", "hello");

        webSocketController.sendGuess(ROOM_CODE, headerAccessor, principal, payload);

        verifyNoInteractions(userRepository);
        log.info("Verified: Payload without 'message' key ignored");
    }
    

    @Test
    @DisplayName("Should truncate long guess messages to 250 characters")
    void sendGuess_ShouldTruncateLongMessage() {
        log.info("Running: sendGuess_ShouldTruncateLongMessage");
        String longMessage = "a".repeat(300);
        Map<String, String> payload = Map.of("message", longMessage);
        
        User mockUser = new User();
        mockUser.setUserId(1L);
        
        Session mockSession = new Session();
        mockSession.setSessionId(99L);

        when(userRepository.findByEmail(EMAIL)).thenReturn(mockUser);
        when(sessionService.getActiveSession(ROOM_CODE)).thenReturn(mockSession);

        webSocketController.sendGuess(ROOM_CODE, headerAccessor, principal, payload);

        verify(gameRoundManager).processGuess(
            eq(99L), eq(ROOM_CODE), eq(1L), any(), any(), 
            argThat(msg -> {
                log.info("Inspecting truncated message length: {}", msg.length());
                return msg.length() == 250;
            })
        );
    }

    @Test
    @DisplayName("Should broadcast as CHAT_MESSAGE when no session is active")
    void sendGuess_NoActiveSession_BroadcastsChat() {
        log.info("Running: sendGuess_NoActiveSession_BroadcastsChat");
        Map<String, String> payload = Map.of("message", "Hello World");
        User mockUser = new User();
        mockUser.setUsername(USERNAME);
        
        when(userRepository.findByEmail(EMAIL)).thenReturn(mockUser);
        when(sessionService.getActiveSession(ROOM_CODE)).thenReturn(null);

        webSocketController.sendGuess(ROOM_CODE, headerAccessor, principal, payload);

        verify(messagingTemplate).convertAndSend(eq("/topic/room/" + ROOM_CODE), (Object) mapCaptor.capture());
        
        Map<String, Object> broadcast = mapCaptor.getValue();
        log.info("Captured Broadcast Payload: {}", broadcast);
        
        assertThat(broadcast.get("type")).isEqualTo("CHAT_MESSAGE");
        assertThat(broadcast.get("message")).isEqualTo("Hello World");
    }


}

