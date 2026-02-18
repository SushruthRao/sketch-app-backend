package com.project.drawguess.globalexceptionhandler;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.project.drawguess.exception.ErrorResponse;
import com.project.drawguess.exception.ResourceNotFoundException;
import com.project.drawguess.exception.UserWithEmailAlreadyRegisteredException;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler({SignatureException.class, MalformedJwtException.class})
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleInvalidJwtException(Exception e) {
		log.warn("Invalid JWT token: {}", e.getMessage());
		return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Invalid JWT token");
	}

	@ExceptionHandler(ExpiredJwtException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleExpiredJwtException(ExpiredJwtException e) {
		log.warn("Expired JWT token: {}", e.getMessage());
		return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "JWT token has expired");
	}

	@ExceptionHandler(BadCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleBadCredentialsException(BadCredentialsException e) {
		log.warn("Bad credentials: {}", e.getMessage());
		return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Invalid email or password");
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

	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public ErrorResponse handleGeneralException(Exception e) {
		log.error("Unexpected error: {}", e.getMessage(), e);
		return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", "An unexpected error occurred");
	}
}
