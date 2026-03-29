package com.project.drawguess.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Represents a single row in the admin rooms table.
// Includes only what's needed to display and act on a room — not the full Room entity.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminRoomDto {
    private String roomCode;
    private String hostUsername;
    private String status;
    private long playerCount;
    private int currentRound;
    private int totalRounds;
    private boolean isPublic;
    private LocalDateTime createdAt;
}
