package com.project.drawguess.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminStatsDto {
    private long totalUsers;
    private long newUsersToday;       // accounts registered since midnight
    private long waitingRooms;
    private long playingRooms;
    private long finishedRooms;
    private long totalRooms;
    private long activeSessions;
    private long totalSessions;
    private long totalRoundsPlayed;   // sum of currentRound across all sessions
    private long activePlayers;
}
