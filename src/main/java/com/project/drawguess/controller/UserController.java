package com.project.drawguess.controller;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.drawguess.dto.AuthRequestDto;
import com.project.drawguess.dto.AuthResponseDto;
import com.project.drawguess.dto.RefreshResponseDto;
import com.project.drawguess.dto.RegisterRequestDto;
import com.project.drawguess.exception.ErrorResponse;
import com.project.drawguess.exception.UserWithEmailAlreadyRegisteredException;
import com.project.drawguess.jwtfilter.JwtUtil;
import com.project.drawguess.service.RefreshTokenService;
import com.project.drawguess.service.impl.UserServiceImpl;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/user")
public class UserController {

	private static final String REFRESH_COOKIE_NAME = "refresh_token";
	private static final String REFRESH_COOKIE_PATH = "/user/";

	private final AuthenticationManager authenticationManager;
	private final JwtUtil jwtUtil;
	private final UserServiceImpl userServiceImpl;
	private final RefreshTokenService refreshTokenService;
	private final boolean cookieSecure;

	public UserController(
			AuthenticationManager authenticationManager,
			JwtUtil jwtUtil,
			UserServiceImpl userServiceImpl,
			RefreshTokenService refreshTokenService,
			@Value("${app.cookie.secure}") boolean cookieSecure) {
		this.authenticationManager = authenticationManager;
		this.jwtUtil = jwtUtil;
		this.userServiceImpl = userServiceImpl;
		this.refreshTokenService = refreshTokenService;
		this.cookieSecure = cookieSecure;
	}

	@PostMapping("/register")
	public ResponseEntity<?> registerUser(@RequestBody @Valid RegisterRequestDto registerRequestDto) throws UserWithEmailAlreadyRegisteredException
	{
		String response = userServiceImpl.registerUser(registerRequestDto);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody @Valid AuthRequestDto authRequestDto, HttpServletResponse response) throws BadCredentialsException, Exception {

		authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(authRequestDto.getEmail(), authRequestDto.getPassword()));

		String accessToken = jwtUtil.generateAccessToken(authRequestDto.getEmail());
		String rawRefreshToken = refreshTokenService.createRefreshToken(authRequestDto.getEmail());

		addRefreshCookie(response, rawRefreshToken);

		AuthResponseDto authResponse = new AuthResponseDto();
		authResponse.setUserName(userServiceImpl.fetchUsername(authRequestDto.getEmail()));
		authResponse.setAccessToken(accessToken);
		authResponse.setExpiresIn(jwtUtil.getAccessTokenExpirationMs());

		return ResponseEntity.ok(authResponse);
	}

	@PostMapping("/refresh")
	public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
		String rawRefreshToken = extractRefreshCookie(request);

		if (rawRefreshToken == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ErrorResponse(401, "Unauthorized", "No refresh token"));
		}

		Optional<String> newRawRefreshToken = refreshTokenService.validateAndRotate(rawRefreshToken);

		if (newRawRefreshToken.isEmpty()) {
			clearRefreshCookie(response);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new ErrorResponse(401, "Unauthorized", "Refresh token invalid or expired"));
		}

		String email = refreshTokenService.getUsernameFromToken(newRawRefreshToken.get());
		String newAccessToken = jwtUtil.generateAccessToken(email);

		addRefreshCookie(response, newRawRefreshToken.get());

		RefreshResponseDto refreshResponse = new RefreshResponseDto();
		refreshResponse.setAccessToken(newAccessToken);
		refreshResponse.setExpiresIn(jwtUtil.getAccessTokenExpirationMs());

		return ResponseEntity.ok(refreshResponse);
	}

	@PostMapping("/logout")
	public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
		String rawRefreshToken = extractRefreshCookie(request);
		if (rawRefreshToken != null) {
			refreshTokenService.revokeToken(rawRefreshToken);
		}
		clearRefreshCookie(response);
		return ResponseEntity.ok(Map.of("message", "Logged out"));
	}

	@GetMapping("/me")
	public ResponseEntity<?> me(@AuthenticationPrincipal UserDetails userDetails) {
		String email = userDetails.getUsername();
		String userName = userServiceImpl.fetchUsername(email);
		return ResponseEntity.ok(Map.of("userName", userName));
	}

	private String extractRefreshCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
					return cookie.getValue();
				}
			}
		}
		return null;
	}

	private void addRefreshCookie(HttpServletResponse response, String tokenValue) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(REFRESH_COOKIE_NAME, tokenValue)
				.httpOnly(true)
				.secure(cookieSecure)
				.path(REFRESH_COOKIE_PATH)
				.maxAge(Duration.ofMillis(jwtUtil.getRefreshTokenExpirationMs()));

		if (cookieSecure) {
			builder.sameSite("None");
		} else {
			builder.sameSite("Lax");
		}

		response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
	}

	private void clearRefreshCookie(HttpServletResponse response) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
				.httpOnly(true)
				.secure(cookieSecure)
				.path(REFRESH_COOKIE_PATH)
				.maxAge(0);

		if (cookieSecure) {
			builder.sameSite("None");
		} else {
			builder.sameSite("Lax");
		}

		response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
	}
}
