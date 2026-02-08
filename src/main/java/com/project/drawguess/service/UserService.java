package com.project.drawguess.service;

import com.project.drawguess.dto.RegisterRequestDto;
import com.project.drawguess.exception.UserWithEmailAlreadyRegisteredException;

public interface UserService {

	String fetchUsername(String email);

	String registerUser(RegisterRequestDto registerRequestDto) throws UserWithEmailAlreadyRegisteredException;

}
