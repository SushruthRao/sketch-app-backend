package com.project.drawguess.globalexceptionhandler;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import com.project.drawguess.exception.ErrorResponse;
import com.project.drawguess.exception.ResourceNotFoundException;
import com.project.drawguess.exception.UserWithEmailAlreadyRegisteredException;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralised exception → HTTP response mapping for all REST endpoints.
 *
 * Handlers are ordered from most-specific to least-specific; the catch-all
 * {@code handleGeneralException} guarantees we never leak a stack trace to
 * the client. Every handler logs at an appropriate level so operators can
 * distinguish client errors (warn) from genuine server faults (error).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	// ---- JWT / auth errors --------------------------------------------------

	@ExceptionHandler({SignatureException.class, MalformedJwtException.class, UnsupportedJwtException.class})
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleInvalidJwtException(Exception e) {
		log.warn("Invalid JWT token ({}): {}", e.getClass().getSimpleName(), e.getMessage());
		return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Invalid JWT token");
	}

	@ExceptionHandler(ExpiredJwtException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleExpiredJwtException(ExpiredJwtException e) {
		// Frontend axios interceptor uses this to trigger a refresh flow
		log.info("Expired JWT token: {}", e.getMessage());
		return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "JWT token has expired");
	}

	@ExceptionHandler(BadCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleBadCredentialsException(BadCredentialsException e) {
		log.warn("Bad credentials: {}", e.getMessage());
		return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Invalid email or password");
	}

	@ExceptionHandler(InternalAuthenticationServiceException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleInternalAuthException(InternalAuthenticationServiceException e) {
		// Usually thrown when UserDetailsService cannot load the user during login
		log.warn("Authentication service failure: {}", e.getMessage());
		return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Authentication failed");
	}

	@ExceptionHandler(AuthenticationException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleAuthenticationException(AuthenticationException e) {
		log.warn("Authentication error ({}): {}", e.getClass().getSimpleName(), e.getMessage());
		return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Authentication failed");
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
		String firstError = ex.getBindingResult().getAllErrors().stream()
				.map(error -> error.getDefaultMessage())
				.findFirst()
				.orElse("Validation failed");
		log.warn("Validation error: {}", firstError);
		return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Bad Request", firstError);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleIllegalArgumentException(IllegalArgumentException e) {
		log.warn("Bad request: {}", e.getMessage());
		return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Bad Request", e.getMessage());
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handleResourceNotFoundException(ResourceNotFoundException e) {
		log.warn("Resource not found: {}", e.getMessage());
		return new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Not Found", e.getMessage());
	}

	@ExceptionHandler(UserWithEmailAlreadyRegisteredException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ErrorResponse handleUserAlreadyRegisteredException(UserWithEmailAlreadyRegisteredException e) {
		log.warn("Registration conflict: {}", e.getMessage());
		return new ErrorResponse(HttpStatus.CONFLICT.value(), "Conflict", e.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ErrorResponse handleIllegalStateException(IllegalStateException e) {
		log.warn("Conflict: {}", e.getMessage());
		return new ErrorResponse(HttpStatus.CONFLICT.value(), "Conflict", e.getMessage());
	}

	/**
	 * Thrown by Spring when a client disconnects from an SSE stream mid-write.
	 * Not an error — the async response is simply no longer usable. We log at
	 * DEBUG so it never triggers production alerts and deliberately return
	 * {@code void} because the underlying request has already been discarded.
	 */
	@ExceptionHandler(AsyncRequestNotUsableException.class)
	public void handleAsyncRequestNotUsableException(AsyncRequestNotUsableException e) {
	    log.debug("Async request closed by client: {}", e.getMessage());
	}

	/**
	 * Last-resort handler. Logs full stack trace server-side but returns
	 * a generic message so we don't leak internals to the client.
	 */
	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ErrorResponse handleGeneralException(Exception e) {
		log.error("Unexpected error: {}", e.getMessage(), e);
		return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", "An unexpected error occurred");
	}
}