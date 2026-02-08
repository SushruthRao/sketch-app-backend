package com.project.drawguess.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.project.drawguess.enums.SessionStatus;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sessions_table")
@Data
@NoArgsConstructor
public class Session {
		
  	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sessionId;
  	
  	@ManyToOne(fetch = FetchType.LAZY)
  	@JoinColumn(name = "room_id", nullable = false)
  	private Room room;
  	
  	@Enumerated(EnumType.STRING)
  	@Column(nullable = false)
  	private SessionStatus status = SessionStatus.WAITING;
  	
  	@Column(nullable = false)
  	private LocalDateTime startedAt;
  	
  	private LocalDateTime endedAt;
  	
  	@Column(nullable = false)
  	private Integer totalRounds = 0;

  	@Column(nullable = false)
  	private Integer currentRound = 0;

  	@OneToMany(mappedBy = "session", cascade = CascadeType.ALL)
  	private List<UserSession> userSessions = new ArrayList<>();

  	public Session(Room room, Integer totalRounds)
  	{
  		this.room = room;
  		this.totalRounds = totalRounds;
  		this.currentRound = 0;
  		this.status = SessionStatus.WAITING;
  		this.startedAt = LocalDateTime.now();

  	}
  	
  	@PrePersist
  	protected void onCreated() {
  		if(startedAt == null)
  		{
  			startedAt = LocalDateTime.now();
  		}
  	}
  	
  	
}
