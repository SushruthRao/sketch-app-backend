package com.project.drawguess.jwtfilter;

import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility for generating and validating JWT access / refresh tokens.
 *
 * Validation methods deliberately distinguish between the specific JJWT
 * exception types so callers (filters, interceptors, exception handlers)
 * can produce accurate 401 responses instead of an opaque "invalid token".
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String accessSecret;

    @Value("${app.jwt.refresh-secret}")
    private String refreshSecret;
    
    private static final long ACCESS_TOKEN_EXPIRATION_MS = 1000L * 60 * 15;         // 15 minutes
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 1000L * 60 * 60 * 24 * 7; // 7 days

    private Key getAccessSigningKey() {
        return Keys.hmacShaKeyFor(accessSecret.getBytes());
    }

    private Key getRefreshSigningKey() {
        return Keys.hmacShaKeyFor(refreshSecret.getBytes());
    }

    public String generateAccessToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .claim("type", "access")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION_MS))
                .signWith(getAccessSigningKey())
                .compact();
    }

    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .claim("type", "refresh")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION_MS))
                .signWith(getRefreshSigningKey())
                .compact();
    }

    public String extractUsernameFromAccessToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getAccessSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public String extractUsernameFromRefreshToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getRefreshSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    /**
     * Validates an access token against the expected username.
     *
     * Rethrows {@link ExpiredJwtException} so that {@code GlobalExceptionHandler}
     * can return a distinct 401 "token expired" response; all other JJWT
     * signature / format errors collapse to {@code false}.
     *
     * @throws ExpiredJwtException if the token is well-formed but expired
     */
    public boolean isAccessTokenValid(String token, String username) {
        try {
            String extractedUsername = extractUsernameFromAccessToken(token);
            Date expiration = Jwts.parserBuilder()
                    .setSigningKey(getAccessSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();
            return extractedUsername.equals(username) && !expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            // Let the filter/handler see expiry explicitly so it becomes a 401
            log.debug("Access token expired for user {}: {}", username, e.getMessage());
            throw e;
        } catch (SignatureException | MalformedJwtException | UnsupportedJwtException e) {
            log.warn("Access token rejected ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("Access token was null or empty: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates a refresh token's signature / structure only (DB lookup is
     * done separately by {@code RefreshTokenService}).
     *
     * Uses specific exception types so we can log why a refresh failed —
     * critical for diagnosing user logout loops in production.
     */
    public boolean isRefreshTokenJwtValid(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getRefreshSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.info("Refresh token expired: {}", e.getMessage());
            return false;
        } catch (SignatureException | MalformedJwtException | UnsupportedJwtException e) {
            log.warn("Refresh token rejected ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("Refresh token was null or empty: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            // Defensive catch-all for any other JJWT exception subtype
            log.warn("Refresh token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public long getAccessTokenExpirationMs() {
        return ACCESS_TOKEN_EXPIRATION_MS;
    }

    public long getRefreshTokenExpirationMs() {
        return REFRESH_TOKEN_EXPIRATION_MS;
    }
}