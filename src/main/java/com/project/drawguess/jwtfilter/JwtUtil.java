package com.project.drawguess.jwtfilter;

import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

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
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshTokenJwtValid(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getRefreshSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
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
