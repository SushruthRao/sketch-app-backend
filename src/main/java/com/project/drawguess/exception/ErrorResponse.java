package com.project.drawguess.exception;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {

	private Integer statusCode;
	private String error;
	private String message;
	private String timestamp;

	public ErrorResponse(Integer statusCode, String error, String message) {
		this.statusCode = statusCode;
		this.error = error;
		this.message = message;
		this.timestamp = LocalDateTime.now().toString();
	}
}
