package com.project.drawguess.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.drawguess.dto.AuthRequestDto;
import com.project.drawguess.dto.AuthResponseDto;
import com.project.drawguess.dto.RegisterRequestDto;
import com.project.drawguess.exception.UserWithEmailAlreadyRegisteredException;
import com.project.drawguess.jwtfilter.JwtUtil;
import com.project.drawguess.service.impl.UserServiceImpl;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@RestController
@RequestMapping("/user")
public class UserController {

	private AuthenticationManager authenticationManager;
	private JwtUtil jwtUtil;
	private UserServiceImpl userServiceImpl;

	@PostMapping("/register")
	public ResponseEntity<?> registerUser(@RequestBody @Valid RegisterRequestDto registerRequestDto) throws UserWithEmailAlreadyRegisteredException 
	{
		String response = userServiceImpl.registerUser(registerRequestDto);
		return ResponseEntity.ok(response);
	}
	

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody @Valid AuthRequestDto authRequestDto) throws BadCredentialsException, Exception {

		authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(authRequestDto.getEmail(), authRequestDto.getPassword()));
		
		String token = jwtUtil.generateToken(authRequestDto.getEmail());
		AuthResponseDto response = new AuthResponseDto();
		response.setJsonToken(token);
		response.setUserName(userServiceImpl.fetchUsername(authRequestDto.getEmail()));
		
		return ResponseEntity.ok(response);

	}
	
}
