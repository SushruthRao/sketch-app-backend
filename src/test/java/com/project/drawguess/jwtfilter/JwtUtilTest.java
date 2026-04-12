package com.project.drawguess.jwtfilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;

/**
 * Pure unit tests for {@link JwtUtil}. No Spring context — fields are set
 * reflectively so the test runs in milliseconds and pins down the exact
 * branches that drive 401 responses in production.
 */
class JwtUtilTest {

	private static final String ACCESS_SECRET = "unit-test-access-secret-must-be-long-enough-for-hmac-sha";
	private static final String REFRESH_SECRET = "unit-test-refresh-secret-must-be-long-enough-for-hmac-sha";

	private JwtUtil jwtUtil;

	@BeforeEach
	void setUp() throws Exception {
		jwtUtil = new JwtUtil();
		setField("accessSecret", ACCESS_SECRET);
		setField("refreshSecret", REFRESH_SECRET);
	}

	private void setField(String name, String value) throws Exception {
		Field f = JwtUtil.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(jwtUtil, value);
	}

	@Test
	void generatedAccessTokenRoundTripsToSameUsername() {
		String token = jwtUtil.generateAccessToken("alice@example.com");
		assertEquals("alice@example.com", jwtUtil.extractUsernameFromAccessToken(token));
		assertTrue(jwtUtil.isAccessTokenValid(token, "alice@example.com"));
	}

	@Test
	void accessTokenFailsForWrongUsername() {
		String token = jwtUtil.generateAccessToken("alice@example.com");
		assertFalse(jwtUtil.isAccessTokenValid(token, "bob@example.com"));
	}

	@Test
	void expiredAccessTokenRethrowsExpiredJwtException() {
		// Hand-craft a token that's already expired using the same secret.
		Key key = Keys.hmacShaKeyFor(ACCESS_SECRET.getBytes());
		String expired = Jwts.builder()
				.setSubject("alice@example.com")
				.claim("type", "access")
				.setIssuedAt(new Date(System.currentTimeMillis() - 60_000))
				.setExpiration(new Date(System.currentTimeMillis() - 30_000))
				.signWith(key)
				.compact();

		assertThrows(ExpiredJwtException.class,
				() -> jwtUtil.isAccessTokenValid(expired, "alice@example.com"));
	}

	@Test
	void malformedAccessTokenReturnsFalse() {
		assertFalse(jwtUtil.isAccessTokenValid("not.a.jwt", "alice@example.com"));
	}

	@Test
	void nullAccessTokenReturnsFalse() {
		assertFalse(jwtUtil.isAccessTokenValid(null, "alice@example.com"));
	}

	@Test
	void refreshTokenValidationAcceptsFreshlyIssuedToken() {
		String token = jwtUtil.generateRefreshToken("alice@example.com");
		assertTrue(jwtUtil.isRefreshTokenJwtValid(token));
	}

	@Test
	void refreshTokenSignedWithWrongKeyIsRejected() {
		Key otherKey = Keys.hmacShaKeyFor("totally-different-secret-still-long-enough-for-hs256".getBytes());
		String bogus = Jwts.builder()
				.setSubject("alice@example.com")
				.setExpiration(new Date(System.currentTimeMillis() + 60_000))
				.signWith(otherKey)
				.compact();

		assertFalse(jwtUtil.isRefreshTokenJwtValid(bogus));
	}

	@Test
	void refreshTokenValidationReturnsFalseForNull() {
		assertFalse(jwtUtil.isRefreshTokenJwtValid(null));
	}
}
