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
    private String secret;
    
	private static final long EXPIRATION_TIME = 1000 * 60 * 60 * 3; // millisecond * second * min * hours

	private Key getSigningKey() {
        byte[] keyBytes = secret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }
	
	public String generateToken(String username)
	{
		return Jwts.builder()
				.setSubject(username)
				.setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
				.signWith(getSigningKey())
				.compact();
	}
	
	public String extractUsername(String token)
	{
		return Jwts.parserBuilder()
				.setSigningKey(getSigningKey())
				.build()
				.parseClaimsJws(token)
				.getBody()
				.getSubject();
	}
	
	public boolean isTokenExpired(String token)
	{
		Date expiration = Jwts.parserBuilder()
				.setSigningKey(getSigningKey())
				.build()
				.parseClaimsJws(token)
				.getBody()
				.getExpiration();
		return expiration.before(new Date());
	}
	
	public boolean isTokenValid(String token, String username)
	{
		return extractUsername(token).equals(username) && !isTokenExpired(token);
	}
}


