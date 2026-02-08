package com.project.drawguess.globalexceptionhandler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.project.drawguess.exception.ErrorResponse;
import com.project.drawguess.exception.UserWithEmailAlreadyRegisteredException;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(value = { SignatureException.class, MalformedJwtException.class })
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleInvalidJwtSignatureException(Exception e) {
		return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Invalid JWT token: " + e.getMessage());
	}

	@ExceptionHandler(value = ExpiredJwtException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleExpiredJwtException(ExpiredJwtException e) {
		return new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "JWT token has expired: " + e.getMessage());
	}

	@ExceptionHandler(value = BadCredentialsException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleBadCredentialsException(BadCredentialsException e) {
		return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new HashMap<>();
		ex.getBindingResult().getAllErrors().forEach(error -> {
			String fieldName = ((FieldError) error).getField();
			String errorMessage = error.getDefaultMessage();
			fieldErrors.put(fieldName, errorMessage);
		});
		Map<String, Object> response = new HashMap<>();
		response.put("error", fieldErrors);

		return response;
	}

	@ExceptionHandler(value = Exception.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleGeneralException(Exception e) {
		return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage());
	}
	
	
	@ExceptionHandler(value = IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleIllegalArgumentException(Exception e) {
		return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage());
	}
	

	@ExceptionHandler(value = UserWithEmailAlreadyRegisteredException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleUserWithEmailAlreadyRegisteredException(UserWithEmailAlreadyRegisteredException e) {
		return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage());
	}
	

}
