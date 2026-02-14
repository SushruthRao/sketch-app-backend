package com.project.drawguess.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.project.drawguess.enums.SessionStatus;
import com.project.drawguess.model.Room;
import com.project.drawguess.model.Session;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
	
	Optional<Session> findByRoomAndStatus(Room room, SessionStatus status);
	
	@Query("SELECT s FROM Session s WHERE s.room.roomId = :roomId AND s.status = 'ACTIVE'")
	Optional<Session> findActiveSessionByRoomId(Long roomId);
	
}
