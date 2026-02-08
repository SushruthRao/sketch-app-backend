package com.project.drawguess.service.impl;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.drawguess.dto.RegisterRequestDto;
import com.project.drawguess.exception.UsernameAlreadyTakenException;
import com.project.drawguess.exception.UserWithEmailAlreadyRegisteredException;
import com.project.drawguess.model.User;
import com.project.drawguess.repository.UserRepository;
import com.project.drawguess.service.UserService;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserDetailsService, UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User existingUser = userRepository.findByEmail(username);
		if (existingUser == null) {
			throw new UsernameNotFoundException(username + " not found in database");
		}

		List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

		return new org.springframework.security.core.userdetails.User(existingUser.getEmail(),
				existingUser.getPasswordHash(), authorities);
	}

	@Override
	public String fetchUsername(String email) {
		User existingUser = userRepository.findByEmail(email);
		return existingUser.getUsername();
	}

	@Override
	@Transactional
	public String registerUser(RegisterRequestDto registerRequestDto)
			throws UserWithEmailAlreadyRegisteredException, UsernameAlreadyTakenException {

		String email = registerRequestDto.getEmail().trim().toLowerCase();
		String username = registerRequestDto.getUsername().trim();

		if (userRepository.existsByEmail(email)) {
			throw new UserWithEmailAlreadyRegisteredException("User with email " + email + " already registered");
		}

		if (userRepository.existsByUsername(username)) {
			throw new UsernameAlreadyTakenException("Username '" + username + "' is already taken");
		}

		User newUser = new User();
		newUser.setEmail(email);
		newUser.setUsername(username);
		newUser.setPasswordHash(passwordEncoder.encode(registerRequestDto.getPassword()));
		userRepository.save(newUser);

		return "Registered user successfully!";
	}

}
