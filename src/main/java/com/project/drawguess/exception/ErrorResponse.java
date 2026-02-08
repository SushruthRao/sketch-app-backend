package com.project.drawguess.exception;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

	private Integer statusCode;
	private String message;
	private Map<String, String> fieldErrors;

	public ErrorResponse(String message) {
		this.message = message;
	}

	public ErrorResponse(Integer statusCode, String message) {
		this.statusCode = statusCode;
		this.message = message;
	}
}
