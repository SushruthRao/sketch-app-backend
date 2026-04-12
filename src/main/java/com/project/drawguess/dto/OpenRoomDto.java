package com.project.drawguess.dto;

/**
 * Minimal projection of an open room for the lobby list.
 *
 * Pushed through {@code OpenRoomSseService} on every room state change so
 * the lobby reflects real-time player counts without polling.
 *
 * @param roomCode      6-digit room code used in URLs
 * @param hostUsername  display name of the room creator
 * @param playerCount   currently connected (active) players
 * @param maxPlayers    room capacity — driven by {@code app.room.max-players}
 * @param createdAt     ISO-8601 timestamp; sorted client-side for display
 */
public record OpenRoomDto(
		String roomCode,
		String hostUsername,
		int playerCount,
		int maxPlayers,
		String createdAt
) {
}
