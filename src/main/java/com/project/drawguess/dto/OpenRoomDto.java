package com.project.drawguess.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OpenRoomDto {
	private String roomCode;
	private String hostUsername;
	private int playerCount;
	private int maxPlayers;
	private String createdAt;
}
