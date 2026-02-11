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
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_sessions_table", uniqueConstraints = {
		@UniqueConstraint(columnNames = {"user_id", "session_id"})
})
@Data
@NoArgsConstructor
public class UserSession {
		
  	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userSessionId;
  	
  	@ManyToOne(fetch = FetchType.LAZY)
  	@JoinColumn(name = "user_id", nullable = false)
  	private User user;
  	
  	@ManyToOne(fetch = FetchType.LAZY)
  	@JoinColumn(name = "session_id", nullable = false)
  	private Session session;
  	
  	@Column(nullable = false)
  	private Integer score = 0;
  	
  	@Column(nullable = false)
  	private Boolean isHost = false;
  	
  	@Column(nullable = false)
  	private LocalDateTime joinedAt;
  	
  	private LocalDateTime leftAt;
  	
  	@Column(nullable = false)
  	private Boolean isActive = true;
  	
  	public UserSession(User user, Session session, Boolean isHost)
  	{
  		this.user = user;
  		this.session = session;
  		this.isHost = isHost;
  		this.score = 0;
  		this.joinedAt = LocalDateTime.now();
  		this.isActive = true;
  	}
  	
  	public void addScore(int points)
  	{
  		this.score += points;
  	}
  	
 	@PrePersist
  	protected void onCreated() {
  		if(joinedAt == null)
  		{
  			joinedAt = LocalDateTime.now();
  		}
  	}
}
