package com.project.drawguess.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.project.drawguess.model.RefreshToken;
import com.project.drawguess.model.User;

import jakarta.transaction.Transactional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserAndRevokedFalse(User user);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR rt.revoked = true")
    int deleteExpiredAndRevoked(@Param("now") LocalDateTime now);
}
