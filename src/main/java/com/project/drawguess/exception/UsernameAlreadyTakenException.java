package com.project.drawguess.exception;

import lombok.Data;

@Data
public class UsernameAlreadyTakenException extends Exception {

	private String message;

	public UsernameAlreadyTakenException() {}

	public UsernameAlreadyTakenException(String message) {
		this.message = message;
	}
}
