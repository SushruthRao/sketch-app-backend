package com.project.drawguess.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.project.drawguess.enums.RoomStatus;
import com.project.drawguess.model.Room;
import java.util.List;


@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
	List<Room> findByRoomCode(String roomCode);
	boolean existsByRoomCode(String roomCode);
	List<Room> findTop5ByStatusOrderByCreatedAtDesc(RoomStatus status);
	
}