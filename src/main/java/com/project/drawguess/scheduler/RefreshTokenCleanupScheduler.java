package com.project.drawguess.scheduler;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.project.drawguess.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupScheduler {

	private final RefreshTokenRepository refreshTokenRepository;

	@Scheduled(cron = "0 0 3 * * *")
	public void cleanupExpiredAndRevokedTokens() {
		int deleted = refreshTokenRepository.deleteExpiredAndRevoked(LocalDateTime.now());
		log.info("Refresh token cleanup: removed {} expired/revoked tokens", deleted);
	}
}
