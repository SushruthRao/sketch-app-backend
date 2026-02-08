package com.project.drawguess.exception;


import lombok.Data;

@Data
public class UserWithEmailAlreadyRegisteredException extends Exception {
	
	private String message;	
	
	public UserWithEmailAlreadyRegisteredException () {}
	public UserWithEmailAlreadyRegisteredException (String message)
	{
		this.message=message;
	}
}