package com.project.drawguess.exception;

import java.time.LocalDateTime;

/**
 * Canonical JSON error body returned by {@code GlobalExceptionHandler},
 * {@code CustomAuthenticationEntryPoint}, and {@code CustomAccessDeniedHandler}.
 *
 * The frontend's axios wrapper ({@code ApiService.jsx}) extracts
 * {@code message} to feed directly into a toast, so this shape is part of
 * the public API contract.
 *
 * A 3-arg convenience constructor is provided so handlers don't have to
 * compute the timestamp themselves.
 *
 * @param statusCode  HTTP status (duplicated in the body for logging convenience)
 * @param error       short category string ("Unauthorized", "Not Found", …)
 * @param message     human-readable message safe to display to end users
 * @param timestamp   ISO-8601 time the error was produced server-side
 */
public record ErrorResponse(
		Integer statusCode,
		String error,
		String message,
		String timestamp
) {
	public ErrorResponse(Integer statusCode, String error, String message) {
		this(statusCode, error, message, LocalDateTime.now().toString());
	}
}
