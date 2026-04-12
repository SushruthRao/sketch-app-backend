package com.project.drawguess.dto;

/**
 * Response payload for {@code POST /user/refresh}.
 *
 * The new refresh token itself is rotated back via an {@code HttpOnly}
 * cookie (not via this body), so the frontend never sees or stores it in JS.
 *
 * @param accessToken  freshly minted short-lived access JWT
 * @param expiresIn    access token lifetime in milliseconds
 */
public record RefreshResponseDto(
		String accessToken,
		long expiresIn
) {
}
