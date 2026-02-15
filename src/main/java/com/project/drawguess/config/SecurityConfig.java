package com.project.drawguess.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.project.drawguess.jwtfilter.JwtRequestFilter;
import com.project.drawguess.service.impl.UserServiceImpl;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private UserServiceImpl userServiceImpl;
	private JwtRequestFilter jwtRequestFilter;
	private final AuthenticationEntryPoint authenticationEntryPoint;
	private final AccessDeniedHandler accessDeniedHandler;

	public SecurityConfig(@Lazy UserServiceImpl userServiceImpl, JwtRequestFilter jwtRequestFilter,
			AuthenticationEntryPoint authenticationEntryPoint, AccessDeniedHandler accessDeniedHandler) {

		this.userServiceImpl = userServiceImpl;
		this.jwtRequestFilter = jwtRequestFilter;
		this.authenticationEntryPoint = authenticationEntryPoint;
		this.accessDeniedHandler = accessDeniedHandler;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
			.cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(corsConfigurationSource()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(
						auth -> 
						auth.requestMatchers("/health").permitAll()
						.requestMatchers("/user/register", "/user/login").permitAll()
						.requestMatchers("/ws/**").permitAll() 
						.anyRequest().authenticated()
						);
								
		http.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler)

		);
		http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}
	
//	@Bean
//	public CorsConfigurationSource corsConfigurationSource() {
//	    CorsConfiguration corsConfiguration = new CorsConfiguration();
//
//	    corsConfiguration.setAllowedOriginPatterns(List.of("*")); 
//	    corsConfiguration.setAllowedMethods(List.of("*"));
//	    corsConfiguration.setAllowedHeaders(List.of("*"));
//	    corsConfiguration.setAllowCredentials(true);
//
//	    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//	    source.registerCorsConfiguration("/**", corsConfiguration);
//	    return source;
//	}
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
	    CorsConfiguration corsConfiguration = new CorsConfiguration();

	    corsConfiguration.setAllowedOrigins(
	        List.of("https://sketch-app-frontend.vercel.app")
	    );

	    corsConfiguration.setAllowedMethods(
	        List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
	    );

	    corsConfiguration.setAllowedHeaders(List.of("*"));
	    corsConfiguration.setAllowCredentials(true);

	    UrlBasedCorsConfigurationSource source =
	        new UrlBasedCorsConfigurationSource();

	    source.registerCorsConfiguration("/**", corsConfiguration);

	    return source;
	}

	
	
	@Bean
	public AuthenticationManager authManager(HttpSecurity http) throws Exception {
		AuthenticationManagerBuilder authenticationManagerBuilder = http
				.getSharedObject(AuthenticationManagerBuilder.class);
		authenticationManagerBuilder.userDetailsService(userServiceImpl).passwordEncoder(passwordEncoder());
		return authenticationManagerBuilder.build();
	}
}
