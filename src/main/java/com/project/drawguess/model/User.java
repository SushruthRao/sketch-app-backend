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
	  	
}
