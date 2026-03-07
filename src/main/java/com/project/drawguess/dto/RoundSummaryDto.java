package com.project.drawguess.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoundSummaryDto {
    private Long roundRecordId;
    private Long sessionId;
    private Integer roundNumber;
    private String word;
    private String drawerUsername;
    private String correctGuessersJson;
    private String endReason;
    private LocalDateTime createdAt;
}
