package com.project.drawguess.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PublicRoomDto {
	private String roomCode;
	private String hostUsername;
	private int playerCount;
	private int maxPlayers;
	private String createdAt;
}
