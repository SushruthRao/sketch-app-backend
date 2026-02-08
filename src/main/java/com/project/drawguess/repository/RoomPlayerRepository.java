package com.project.drawguess.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.project.drawguess.model.Room;
import com.project.drawguess.model.RoomPlayer;
import com.project.drawguess.model.User;

@Repository
public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, Long> {
	List<RoomPlayer> findByRoomAndIsActive(Room room, Boolean isActive);
	Optional<RoomPlayer> findByWebsocketSessionId(String websocketSessionId);
	long countByRoomAndIsActive(Room room, Boolean isActive);
	List<RoomPlayer> findByRoomAndUser(Room room, User user);
}
