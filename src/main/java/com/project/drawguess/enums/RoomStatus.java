package com.project.drawguess.enums;

/**
 * Room lifecycle stages.
 *
 * <ul>
 *   <li>{@code WAITING} — lobby phase, anyone may join</li>
 *   <li>{@code PLAYING} — game in progress; only players with an
 *       existing {@code RoomPlayer} record may (re)join</li>
 *   <li>{@code FINISHED} — room is closed, no further joins</li>
 * </ul>
 */
public enum RoomStatus {
	WAITING,
	PLAYING,
	FINISHED
}
