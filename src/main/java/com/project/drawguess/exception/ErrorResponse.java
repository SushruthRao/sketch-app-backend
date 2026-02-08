package com.project.drawguess.exception;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {

	private Integer statusCode;
	private String message;
	
	public ErrorResponse(String message)
	{
		this.message = message;
	}
}
