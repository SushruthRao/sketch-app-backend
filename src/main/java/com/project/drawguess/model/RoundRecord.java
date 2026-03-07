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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "round_records", indexes = {
    @Index(name = "idx_round_record_session", columnList = "session_id")
})
@Data
@NoArgsConstructor
public class RoundRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roundRecordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(nullable = false)
    private Integer roundNumber;

    @Column(nullable = false)
    private String word;

    @Column(nullable = false)
    private String drawerUsername;

    @Column(columnDefinition = "TEXT")
    private String correctGuessersJson;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String canvasStrokesJson;

    @Column(nullable = false)
    private String endReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public RoundRecord(Session session, int roundNumber, String word,
                       String drawerUsername, String correctGuessersJson,
                       String canvasStrokesJson, String endReason) {
        this.session = session;
        this.roundNumber = roundNumber;
        this.word = word;
        this.drawerUsername = drawerUsername;
        this.correctGuessersJson = correctGuessersJson;
        this.canvasStrokesJson = canvasStrokesJson;
        this.endReason = endReason;
        this.createdAt = LocalDateTime.now();
    }
}
