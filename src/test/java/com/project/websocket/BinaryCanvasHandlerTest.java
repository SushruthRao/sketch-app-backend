package com.project.websocket;



import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.project.drawguess.game.GameRoundManager;
import com.project.drawguess.model.Session;
import com.project.drawguess.service.impl.CanvasStrokeServiceImpl;
import com.project.drawguess.service.impl.SessionServiceImpl;
import com.project.drawguess.websocket.BinaryCanvasCodec;
import com.project.drawguess.websocket.BinaryCanvasHandler;

@ExtendWith(MockitoExtension.class)
class BinaryCanvasHandlerTest {

    @Mock private CanvasStrokeServiceImpl canvasStrokeService;
    @Mock private GameRoundManager gameRoundManager;
    @Mock private SessionServiceImpl sessionService;
    @Mock private WebSocketSession wsSession;

    @InjectMocks
    private BinaryCanvasHandler canvasHandler;

    private final String SESSION_ID = "sess-123";
    private final String ROOM_CODE = "123456";
    private final String USERNAME = "TestUser";

    @BeforeEach
    void setUp() throws Exception {
        // Only stub what is absolutely required for afterConnectionEstablished
        Map<String, Object> attributes = Map.of("username", USERNAME, "roomCode", ROOM_CODE);
        when(wsSession.getAttributes()).thenReturn(attributes);
        when(wsSession.getId()).thenReturn(SESSION_ID);

        canvasHandler.afterConnectionEstablished(wsSession);
    }

    @Test
    @DisplayName("Should handle and broadcast valid stroke message")
    void handleStroke_Success() throws Exception {
        byte[] payload = new byte[]{0x00, 0x00, 0, 0, 0, 0x05, 0x00, 0x00};
        BinaryMessage message = new BinaryMessage(payload);

        // 2. Use the renamed mock here
        // We return a non-null Session so the code proceeds
        when(sessionService.getActiveSession(anyString())).thenReturn(new Session());
        
        // 3. This must be true so the 'if' check (which checks for !drawer) is skipped
        when(gameRoundManager.isDrawerForRoom(anyString(), anyString())).thenReturn(true);

        canvasHandler.handleBinaryMessage(wsSession, message);

        // 4. Verify it was called
        verify(canvasStrokeService).addStroke(anyString(), any());
    }

    @Test
    @DisplayName("Should reject stroke if user is not the authorized drawer")
    void handleStroke_Unauthorized() throws Exception {
        byte[] payload = new byte[]{0x00, 0x00, 0, 0, 0, 0x05, 0x00, 0x00};
        BinaryMessage message = new BinaryMessage(payload);

        // Use lenient only if the internal logic might exit before calling one of these
        lenient().when(sessionService.getActiveSession(ROOM_CODE)).thenReturn(new Session());
        lenient().when(gameRoundManager.isDrawerForRoom(ROOM_CODE, USERNAME)).thenReturn(false);

        canvasHandler.handleBinaryMessage(wsSession, message);

        verify(canvasStrokeService, never()).addStroke(anyString(), anyMap());
    }

    @Test
    @DisplayName("Should clear canvas when MSG_CLEAR is received")
    void handleClear_Success() throws Exception {
        byte[] payload = new byte[]{ BinaryCanvasCodec.MSG_CLEAR };
        BinaryMessage message = new BinaryMessage(payload);

        when(gameRoundManager.isDrawerForRoom(ROOM_CODE, USERNAME)).thenReturn(true);

        canvasHandler.handleBinaryMessage(wsSession, message);

        verify(canvasStrokeService).clearStrokes(ROOM_CODE);
    }

    @Test
    @DisplayName("Should remove session from internal maps on disconnect")
    void afterConnectionClosed_Cleanup() throws Exception {
        // No extra stubbing needed here
        canvasHandler.afterConnectionClosed(wsSession, CloseStatus.NORMAL);
        
        BinaryMessage message = new BinaryMessage(new byte[]{0x00});
        canvasHandler.handleBinaryMessage(wsSession, message);
        
        // This confirms the handler returned early because maps were cleared
        verifyNoInteractions(sessionService);
    }
}


