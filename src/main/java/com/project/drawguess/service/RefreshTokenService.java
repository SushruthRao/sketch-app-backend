package com.project.drawguess.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.project.drawguess.exception.ResourceNotFoundException;
import com.project.drawguess.jwtfilter.JwtUtil;
import com.project.drawguess.model.RefreshToken;
import com.project.drawguess.model.User;
import com.project.drawguess.repository.RefreshTokenRepository;
import com.project.drawguess.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public String createRefreshToken(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found: " + email);
        }

        String rawToken = jwtUtil.generateRefreshToken(email);
        String tokenHash = hashToken(rawToken);

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(tokenHash);
        entity.setExpiresAt(LocalDateTime.now().plusSeconds(
                jwtUtil.getRefreshTokenExpirationMs() / 1000
        ));
        entity.setRevoked(false);

        refreshTokenRepository.save(entity);
        log.info("Created refresh token for user: {}", email);
        return rawToken;
    }

    public Optional<String> validateAndRotate(String rawToken) {
        if (!jwtUtil.isRefreshTokenJwtValid(rawToken)) {
            log.warn("Refresh token JWT validation failed");
            return Optional.empty();
        }

        String tokenHash = hashToken(rawToken);
        Optional<RefreshToken> stored = refreshTokenRepository.findByTokenHash(tokenHash);

        if (stored.isEmpty() || !stored.get().isValid()) {
            log.warn("Refresh token not found in DB or invalid/revoked");
            return Optional.empty();
        }

        String email = jwtUtil.extractUsernameFromRefreshToken(rawToken);

        // Rotation: revoke old token, issue new one
        stored.get().setRevoked(true);
        refreshTokenRepository.save(stored.get());

        String newRawToken = createRefreshToken(email);
        return Optional.of(newRawToken);
    }

    public void revokeToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            log.info("Revoked refresh token");
        });
    }

    public String getUsernameFromToken(String rawToken) {
        return jwtUtil.extractUsernameFromRefreshToken(rawToken);
    }
}
