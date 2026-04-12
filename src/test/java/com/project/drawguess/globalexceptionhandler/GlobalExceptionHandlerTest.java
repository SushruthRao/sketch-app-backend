package com.project.drawguess.globalexceptionhandler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;

import com.project.drawguess.exception.ErrorResponse;
import com.project.drawguess.exception.ResourceNotFoundException;
import com.project.drawguess.exception.UserWithEmailAlreadyRegisteredException;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;

/**
 * Direct unit tests for {@link GlobalExceptionHandler}. A full MockMvc
 * slice is overkill here — the handler methods are pure functions from
 * exception to {@link ErrorResponse}, so we invoke them directly and
 * assert on the returned shape + status.
 */
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void malformedJwtMapsToUnauthorized() {
		ErrorResponse body = handler.handleInvalidJwtException(new MalformedJwtException("bad"));
		assertThat(body.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
		assertThat(body.error()).isEqualTo("Unauthorized");
		assertThat(body.message()).isEqualTo("Invalid JWT token");
		assertThat(body.timestamp()).isNotBlank();
	}

	@Test
	void expiredJwtHasDedicatedMessage() {
		ErrorResponse body = handler.handleExpiredJwtException(
				new ExpiredJwtException(null, null, "expired"));
		assertThat(body.statusCode()).isEqualTo(401);
		assertThat(body.message()).isEqualTo("JWT token has expired");
	}

	@Test
	void badCredentialsScrubsInternalMessage() {
		ErrorResponse body = handler.handleBadCredentialsException(
				new BadCredentialsException("stack trace leakage risk"));
		assertThat(body.message()).isEqualTo("Invalid email or password");
	}

	@Test
	void internalAuthFailureIs401() {
		ErrorResponse body = handler.handleInternalAuthException(
				new InternalAuthenticationServiceException("db down"));
		assertThat(body.statusCode()).isEqualTo(401);
		assertThat(body.error()).isEqualTo("Unauthorized");
	}

	@Test
	void illegalArgumentBecomes400WithMessage() {
		ErrorResponse body = handler.handleIllegalArgumentException(
				new IllegalArgumentException("Room code must be 6 characters"));
		assertThat(body.statusCode()).isEqualTo(400);
		assertThat(body.message()).isEqualTo("Room code must be 6 characters");
	}

	@Test
	void resourceNotFoundMapsTo404() {
		ErrorResponse body = handler.handleResourceNotFoundException(
				new ResourceNotFoundException("Room 123456 not found"));
		assertThat(body.statusCode()).isEqualTo(404);
		assertThat(body.error()).isEqualTo("Not Found");
	}

	@Test
	void duplicateRegistrationMapsTo409() {
		ErrorResponse body = handler.handleUserAlreadyRegisteredException(
				new UserWithEmailAlreadyRegisteredException("already exists"));
		assertThat(body.statusCode()).isEqualTo(409);
		assertThat(body.error()).isEqualTo("Conflict");
	}

	@Test
	void unexpectedExceptionsAreGenericised() {
		ErrorResponse body = handler.handleGeneralException(
				new NullPointerException("unexpected NPE revealing internal state"));
		assertThat(body.statusCode()).isEqualTo(500);
		assertThat(body.message()).isEqualTo("An unexpected error occurred");
	}
}
