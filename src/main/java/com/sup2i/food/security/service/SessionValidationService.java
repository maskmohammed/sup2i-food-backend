package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.UserStatus;
import com.sup2i.food.security.domain.RefreshToken;
import com.sup2i.food.security.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class SessionValidationService {

    private final RefreshTokenRepository refreshTokenRepository;

    public SessionValidationService(
        RefreshTokenRepository refreshTokenRepository
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public boolean isSessionActive(
        UUID sessionId,
        UUID userId
    ) {
        RefreshToken session =
            refreshTokenRepository
                .findByIdAndUserId(sessionId, userId)
                .orElse(null);

        if (session == null) {
            return false;
        }

        if (session.isRevoked()) {
            return false;
        }

        if (!session.getExpiresAt().isAfter(
            OffsetDateTime.now()
        )) {
            return false;
        }

        return session.getUser().getStatus()
            == UserStatus.ACTIVE;
    }
}