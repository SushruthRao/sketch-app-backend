package com.project.drawguess.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import com.project.drawguess.enums.RoomStatus;
import com.project.drawguess.model.Room;
import com.project.drawguess.model.User; 
import com.project.drawguess.service.impl.RoomServiceImpl;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RoomServiceImpl roomService;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private RoomController roomController;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver mockPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().isAssignableFrom(UserDetails.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return userDetails; 
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(roomController)
                .setCustomArgumentResolvers(mockPrincipalResolver)
                .build();
    }
    
    
    @Test
    void createRoom_ShouldReturnSuccess() throws Exception {
        // Arrange
        Room mockRoom = new Room();
        mockRoom.setRoomCode("123456");
        mockRoom.setRoomId(1L);
        
        when(userDetails.getUsername()).thenReturn("testUser");
        when(roomService.createRoom("testUser")).thenReturn(mockRoom);

        // Act & Assert
        mockMvc.perform(post("/api/rooms/create")
                .principal(() -> "TestUser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.roomCode").value("123456"))
                .andExpect(jsonPath("$.roomId").value(1));
    }

    @Test
    void getRoomDetails_ShouldReturnRoomAndPlayers() throws Exception {
        // Arrange
        String roomCode = "421232";
        Room mockRoom = new Room();
        mockRoom.setRoomCode(roomCode);
        mockRoom.setStatus(RoomStatus.WAITING);
        
        User host = new User();
        host.setUsername("hostUser");
        mockRoom.setHost(host);

        List<Map<String, Object>> players = List.of(Map.of("username", "exampleUser"));

        when(roomService.getRoomByCode(roomCode)).thenReturn(mockRoom);
        when(roomService.getActivePlayers(roomCode)).thenReturn(players);

        mockMvc.perform(get("/api/rooms/{roomCode}", roomCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.room.roomCode").value(roomCode))
                .andExpect(jsonPath("$.room.hostUsername").value("hostUser"))
                .andExpect(jsonPath("$.players[0].username").value("exampleUser"));
    }
}

