package com.project.drawguess.service.impl;

import java.util.List;

import com.project.drawguess.exception.ResourceNotFoundException;
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
import com.project.drawguess.service.UserCacheService;
import com.project.drawguess.service.UserService;


@Service
public class UserServiceImpl implements UserDetailsService, UserService {

	private final UserRepository userRepository;
	private final UserCacheService userCacheService;
	private final PasswordEncoder passwordEncoder;


	public UserServiceImpl(UserRepository userRepository, 
	                       UserCacheService userCacheService, 
	                       PasswordEncoder passwordEncoder) {
	    this.userRepository = userRepository;
	    this.userCacheService = userCacheService;
	    this.passwordEncoder = passwordEncoder;
	}
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

		User existingUser = userCacheService.findByEmail(username);
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
		User existingUser = userCacheService.findByEmail(email);
		if (existingUser == null) {
			throw new ResourceNotFoundException("User not found for email: " + email);
		}
		return existingUser.getUsername();
	}
	
	@Override
	public boolean isUserAdmin(String email)
	{
		User existingUser = userCacheService.findByEmail(email);
		if(existingUser == null)
		{
			throw new ResourceNotFoundException("User not found for email: " + email);
		}
		return existingUser.isAdmin();
	}
	
	@Override
	public String registerUser(RegisterRequestDto registerRequestDto)
			throws UserWithEmailAlreadyRegisteredException {

		User newUser = new User();
		newUser.setEmail(registerRequestDto.email());
		newUser.setUsername(registerRequestDto.username());
		String email = registerRequestDto.email();

		if (userRepository.existsByEmail(email)) {
			throw new UserWithEmailAlreadyRegisteredException("User with email " + email + " already registered");
		}

		newUser.setPasswordHash(passwordEncoder.encode(registerRequestDto.passwordHash()));
		userRepository.save(newUser);
		User savedUser = userRepository.findByEmail(newUser.getEmail());
		userRepository.save(savedUser);
		return "Registered user successfully !" + newUser.toString();
	}

}
