package com.project.drawguess.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Application user. {@code email} is the unique login identifier and also
 * the value stored in JWT {@code sub} claims. {@code passwordHash} is a
 * BCrypt-encoded string — raw passwords are never stored. {@code isAdmin}
 * unlocks the dashboard SSE stream on the frontend.
 */
@Data
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "users_table", indexes = {
    @Index(name = "idx_user_username", columnList = "username")
})
public class User {

	  	@Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    private Long userId;
	  	@Column(name = "username", nullable = false)
	    private String username;
	  	@Column(name = "email", unique = true, nullable = false)
	    private String email;
	  	@Column(name = "password_hash", nullable = false)
	    private String passwordHash;
	  	@Column(name = "is_admin", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
	    private boolean isAdmin = false;
}