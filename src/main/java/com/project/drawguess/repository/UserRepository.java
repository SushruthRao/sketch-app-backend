package com.project.drawguess.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.project.drawguess.dto.UserStats;
import com.project.drawguess.model.User;


@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	@Query(value = "SELECT u.username, COUNT(us.user_session_id) AS total_games_played, " +
	           "MAX(us.score) AS high_score, SUM(us.score) AS total_accumulated_points " +
	           "FROM users_table u LEFT JOIN user_sessions_table us ON u.user_id = us.user_id " +
	           "GROUP BY u.user_id ORDER BY total_games_played DESC", nativeQuery = true)
	List<UserStats> getUserDashboardStats();
	User findByEmail(String email);
	boolean existsByEmail(String email);
	boolean existsByUsername(String username);
}