package com.project.drawguess.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.project.drawguess.enums.RoomStatus;
import com.project.drawguess.model.Room;
import java.util.List;
import java.util.Optional;


@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
	List<Room> findByRoomCode(String roomCode);
	boolean existsByRoomCode(String roomCode);
	List<Room> findTop5ByIsPublicAndStatusOrderByCreatedAtDesc(boolean isPublic, RoomStatus status);

	// Used to look up only active (non-FINISHED) rooms — enables room code reuse
	Optional<Room> findFirstByRoomCodeAndStatusNot(String roomCode, RoomStatus status);
	boolean existsByRoomCodeAndStatusNot(String roomCode, RoomStatus status);

	// Used to clean up abandoned rooms when the host disconnects before joining via WebSocket
	List<Room> findByHostEmailAndStatus(String hostEmail, RoomStatus status);

	// Used by the cleanup scheduler to find all waiting rooms
	List<Room> findByStatus(RoomStatus status);
}
