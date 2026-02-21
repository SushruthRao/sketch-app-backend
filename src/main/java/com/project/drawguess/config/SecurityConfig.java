package com.project.drawguess.config;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
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
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

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
			.cors(cors -> cors.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(
						auth ->
						auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/health").permitAll()
						.requestMatchers("/user/register", "/user/login", "/user/logout", "/user/refresh").permitAll()
						.requestMatchers("/ws/**", "/ws-canvas/**").permitAll()
						.anyRequest().authenticated()
						);

		http.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler)

		);
		http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
		CorsConfiguration corsConfiguration = new CorsConfiguration();
		corsConfiguration.setAllowedOriginPatterns(
			List.of("*")
		);
//		corsConfiguration.setAllowedOrigins(
//				List.of("https://sketch-app-frontend.vercel.app", "https://sketch-vr.vercel.app")
//			);
		corsConfiguration.setAllowedMethods(
			List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
		);
		corsConfiguration.setAllowedHeaders(
			List.of("Authorization", "Content-Type", "Accept")
		);
		corsConfiguration.setAllowCredentials(true);
		corsConfiguration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", corsConfiguration);

		FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
		bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return bean;
	}

	@Bean
	public AuthenticationManager authManager(HttpSecurity http) throws Exception {
		AuthenticationManagerBuilder authenticationManagerBuilder = http
				.getSharedObject(AuthenticationManagerBuilder.class);
		authenticationManagerBuilder.userDetailsService(userServiceImpl).passwordEncoder(passwordEncoder());
		return authenticationManagerBuilder.build();
	}
}
