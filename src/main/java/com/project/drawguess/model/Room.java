package com.project.drawguess.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.project.drawguess.enums.RoomStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "rooms_table")
@NoArgsConstructor
public class Room {

  	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roomId;
  	
  	// unique constraint removed from annotation so closed room codes can be reused.
  	// Run: ALTER TABLE rooms_table DROP INDEX UK_<roomCode_constraint_name>;
  	@Column(nullable = false, length = 6)
  	private String roomCode;
  	
  	@ManyToOne(fetch = FetchType.EAGER)
  	@JoinColumn(name = "host_user_id", nullable = false)
  	private User host;
  	
  	@Enumerated(EnumType.STRING)
  	@Column(nullable = false)
  	private RoomStatus status = RoomStatus.WAITING;
  	
  	
  	@Column(nullable = false)
  	private Boolean isPublic = true;

  	@Column(nullable = false)
  	private LocalDateTime createdAt = LocalDateTime.now();

  	private LocalDateTime closedAt;

  	public Room(String roomCode, User host) {
  		this.roomCode = roomCode;
  		this.host = host;
  		this.status = RoomStatus.WAITING;
  		this.isPublic = true;
  	}

}
