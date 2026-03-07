package com.project.drawguess.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.project.drawguess.dto.RoundSummaryDto;
import com.project.drawguess.model.RoundRecord;

@Repository
public interface RoundRecordRepository extends JpaRepository<RoundRecord, Long> {

    /**
     * Fetch round summaries (no canvasStrokesJson) for a list of session IDs.
     * Used by the match history list endpoint.
     */
    @Query("""
        SELECT new com.project.drawguess.dto.RoundSummaryDto(
            rr.roundRecordId, rr.session.sessionId, rr.roundNumber,
            rr.word, rr.drawerUsername, rr.correctGuessersJson,
            rr.endReason, rr.createdAt)
        FROM RoundRecord rr
        WHERE rr.session.sessionId IN :sessionIds
        ORDER BY rr.session.sessionId DESC, rr.roundNumber ASC
        """)
    List<RoundSummaryDto> findSummariesBySessionIds(@Param("sessionIds") List<Long> sessionIds);
}
