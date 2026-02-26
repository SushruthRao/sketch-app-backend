package com.project.drawguess.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room_player_table", indexes = {
    @Index(name = "idx_room_player_room_active", columnList = "room_id, is_active"),
    @Index(name = "idx_room_player_ws_session", columnList = "websocket_session_id"),
    @Index(name = "idx_room_player_room_user", columnList = "room_id, user_id")
})
@Data
@NoArgsConstructor
public class RoomPlayer {

  	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roomPlayerId;
  	
  	@ManyToOne(fetch = FetchType.EAGER)
  	@JoinColumn(name = "room_id", nullable = false)
  	private Room room;
  	
  	@ManyToOne(fetch = FetchType.EAGER)
  	@JoinColumn(name = "user_id", nullable = false)
  	private User user;
  	
  	@Column(nullable = false)
  	private String websocketSessionId;
  	
  	@Column(nullable = false)
  	private Boolean isActive = true;
  	
  	@Column(nullable = false)
  	private LocalDateTime joinedAt = LocalDateTime.now();
  	
  	private LocalDateTime leftAt;
  	
  	public RoomPlayer(Room room, User user, String websocketSessionId)
  	{
  		this.room = room;
  		this.user = user;
  		this.websocketSessionId = websocketSessionId;
  	}
}
