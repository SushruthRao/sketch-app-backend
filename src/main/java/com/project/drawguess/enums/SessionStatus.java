package com.project.drawguess.enums;

/**
 * Session lifecycle stages. Only one session per room may be {@code ACTIVE}
 * at a time (enforced by {@code SessionRepository#findActiveSessionByRoomId}).
 */
public enum SessionStatus {
	WAITING,
	ACTIVE,
	FINISHED
}
