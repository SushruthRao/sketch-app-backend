package com.project.drawguess.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.project.drawguess.dto.RegisterRequestDto;
import com.project.drawguess.exception.UserWithEmailAlreadyRegisteredException;
import com.project.drawguess.model.User;
import com.project.drawguess.repository.UserRepository;
import com.project.drawguess.service.UserService;


@Service
public class UserServiceImpl implements UserDetailsService, UserService {

	@Autowired
	private UserRepository userRepository;
	

	private final PasswordEncoder passwordEncoder;

	public UserServiceImpl(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

		User existingUser = userRepository.findByEmail(username);
		if (existingUser == null) {
			throw new UsernameNotFoundException(username + " not found in database ");
		}

		List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

		return new org.springframework.security.core.userdetails.User(existingUser.getEmail(),
				existingUser.getPasswordHash(), authorities);
	}

	
	@Override
	public String fetchUsername(String email)
	{
		User existingUser = userRepository.findByEmail(email);
		
		return existingUser.getUsername(); 
	}
	
	@Override
	public String registerUser(RegisterRequestDto registerRequestDto)
			throws UserWithEmailAlreadyRegisteredException {

		User newUser = new User();
		newUser.setEmail(registerRequestDto.getEmail());
		newUser.setUsername(registerRequestDto.getUsername());
		String email = registerRequestDto.getEmail();

		if (userRepository.existsByEmail(email)) {
			throw new UserWithEmailAlreadyRegisteredException("User with email " + email + " already registered");
		}

		newUser.setPasswordHash(passwordEncoder.encode(registerRequestDto.getPasswordHash()));
		userRepository.save(newUser);
		User savedUser = userRepository.findByEmail(newUser.getEmail());
		userRepository.save(savedUser);
		return "Registered user successfully !" + newUser.toString();
	}

}

