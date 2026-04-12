package com.project.drawguess.dto;

/**
 * Response payload for a successful {@code POST /user/login}.
 *
 * @param userName     display name shown in the UI header
 * @param accessToken  short-lived JWT the frontend attaches as a Bearer header
 * @param expiresIn    access token lifetime in milliseconds (used by the
 *                     frontend to schedule a proactive refresh)
 * @param isAdmin      whether the user has admin privileges — drives
 *                     conditional UI rendering
 */
public record AuthResponseDto(
		String userName,
		String accessToken,
		long expiresIn,
		boolean isAdmin
) {
}
