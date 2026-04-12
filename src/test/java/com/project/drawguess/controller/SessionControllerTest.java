package com.project.drawguess.controller;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.project.drawguess.enums.SessionStatus;
import com.project.drawguess.model.Session;
import com.project.drawguess.service.impl.SessionServiceImpl;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    @Mock 
    private SessionServiceImpl sessionServiceImpl;

    @InjectMocks
    private SessionController sessionController;

    @Captor
    private ArgumentCaptor<Map<String, Object>> mapCaptor;

    private final String ROOM_CODE = "ROOM123";

    @Test
    @DisplayName("Should return active session details when session exists")
    void getActiveSession_Success() {
        log.info("Running: getActiveSession_Success - Room: {}", ROOM_CODE);
        
        // Arrange
        Session mockSession = new Session();
        mockSession.setSessionId(101L);
        mockSession.setStatus(SessionStatus.ACTIVE);
        mockSession.setTotalRounds(5);
        mockSession.setCurrentRound(1);
        mockSession.setStartedAt(LocalDateTime.now());

        List<Map<String, Object>> mockPlayers = List.of(Map.of("username", "Player1"));

        when(sessionServiceImpl.getActiveSession(ROOM_CODE)).thenReturn(mockSession);
        when(sessionServiceImpl.getSessionPlayers(101L)).thenReturn(mockPlayers);

        // Act
        ResponseEntity<?> response = sessionController.getActiveSession(ROOM_CODE);

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isEqualTo(true);
        
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("success", true);
        assertThat(body).containsKey("session");
        assertThat(body).containsEntry("players", mockPlayers);
        
        log.info("Success: getActiveSession_Success returned session ID {}", mockSession.getSessionId());
    }

    @Test
    @DisplayName("Should return hasActiveSession false when no session found")
    void getActiveSession_NotFound() {
        log.info("Running: getActiveSession_NotFound");
        
        // Arrange
        when(sessionServiceImpl.getActiveSession(ROOM_CODE)).thenReturn(null);

        // Act
        ResponseEntity<?> response = sessionController.getActiveSession(ROOM_CODE);

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isEqualTo(true);
        
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("success", true);
        assertThat(body).containsEntry("hasActiveSession", false);
        
        verify(sessionServiceImpl, never()).getSessionPlayers(anyLong());
        log.info("Success: getActiveSession_NotFound handled null session correctly");
    }

    @Test
    @DisplayName("Edge Case: Should propagate IllegalStateException from service")
    void getActiveSession_ThrowsException() {
        log.warn("Running: getActiveSession_ThrowsException");
        
        // Arrange
        when(sessionServiceImpl.getActiveSession(ROOM_CODE))
            .thenThrow(new IllegalStateException("Database error"));

        // Act & Assert
        try {
            sessionController.getActiveSession(ROOM_CODE);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("Database error");
        }
        
        log.info("Verified: Exception propagated correctly");
    }
}
