package com.project.drawguess.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.project.drawguess.model.Session;
import com.project.drawguess.model.User;
import com.project.drawguess.model.UserSession;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
	
	@EntityGraph(attributePaths = {"user"})
	List<UserSession> findBySession(Session session);

	Optional<UserSession> findByUserAndSession(User user, Session session);

	@Query("SELECT us FROM UserSession us JOIN FETCH us.user WHERE us.session.id = :sessionId AND us.isActive = true")
	List<UserSession> findActiveUsersBySessionId(Long sessionId);

	@Query("SELECT COUNT(us) FROM UserSession us WHERE us.session.id = :sessionId AND us.isActive = true")
	long countActivePlayersBySessionId(long sessionId);

	@Query("""
		SELECT us FROM UserSession us
		JOIN FETCH us.session s
		JOIN FETCH s.room
		WHERE us.user.email = :email
		ORDER BY us.userSessionId DESC
		""")
	List<UserSession> findByEmailWithSession(@Param("email") String email);

}

