package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.AuthIdentity;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.AuthIdentityRepository;
import com.sup2i.food.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;

@Service
public class AuthenticationCompletionService {

    private final AuthIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuthLoginAuditService auditService;

    public AuthenticationCompletionService(
        AuthIdentityRepository identityRepository,
        UserRepository userRepository,
        RefreshTokenService refreshTokenService,
        AuthLoginAuditService auditService
    ) {
        this.identityRepository =
            identityRepository;
        this.userRepository =
            userRepository;
        this.refreshTokenService =
            refreshTokenService;
        this.auditService =
            auditService;
    }

    @Transactional
    public AuthenticationTokens complete(
        LocalCredentialService.VerifiedLocalIdentity verified,
        String deviceInfo,
        InetAddress ipAddress,
        String userAgent
    ) {
        AuthIdentity identity =
            verified.identity();

        User user =
            verified.user();

        OffsetDateTime now =
            OffsetDateTime.now();

        identity.setLastUsedAt(now);
        user.setLastLoginAt(now);

        identityRepository.save(identity);
        userRepository.save(user);

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                deviceInfo,
                ipAddress
            );

        auditService.recordSuccess(
            user.getId(),
            verified.identifier(),
            ipAddress,
            userAgent
        );

        return tokens;
    }
}