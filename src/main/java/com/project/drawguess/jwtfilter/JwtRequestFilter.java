package com.project.drawguess.jwtfilter;

import java.io.IOException;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.project.drawguess.service.impl.UserServiceImpl;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

	private JwtUtil jwtUtil;
	private UserServiceImpl userServiceImpl;
    private final HandlerExceptionResolver handlerExceptionResolver;

	 public JwtRequestFilter(JwtUtil jwtUtil, @Lazy UserServiceImpl userServiceImpl, HandlerExceptionResolver handlerExceptionResolver) {
	        this.jwtUtil = jwtUtil;
	        this.userServiceImpl = userServiceImpl;
	        this.handlerExceptionResolver = handlerExceptionResolver;
	    }

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return "OPTIONS".equalsIgnoreCase(request.getMethod());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		try {
			String username = null;
			String jwt = null;

			final String authorizationHeader = request.getHeader("Authorization");

			if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
				jwt = authorizationHeader.substring(7);
				username = jwtUtil.extractUsernameFromAccessToken(jwt);
			}

			if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
				UserDetails userDetails = this.userServiceImpl.loadUserByUsername(username);
				if (jwtUtil.isAccessTokenValid(jwt, userDetails.getUsername())) {
					UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails,
							null, userDetails.getAuthorities());
					authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
					SecurityContextHolder.getContext().setAuthentication(authToken);
				}
			}
			chain.doFilter(request, response);
		} catch (Exception e) {
			handlerExceptionResolver.resolveException(request, response, null, e);
		}
	}
}
