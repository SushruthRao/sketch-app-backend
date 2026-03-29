package com.project.drawguess.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.project.drawguess.model.User;

import java.time.LocalDateTime;


@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	User findByEmail(String email);
	boolean existsByEmail(String email);
	boolean existsByUsername(String username);

	// Fuzzy search across username and email — used by the admin user search panel
	Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
		String username, String email, Pageable pageable);

	// Count accounts created within a time window (used for "new users today" stat)
	long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
