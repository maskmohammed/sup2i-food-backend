package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.AuthIdentity;
import com.sup2i.food.identity.domain.AuthProviderType;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.domain.UserStatus;
import com.sup2i.food.identity.repository.AuthIdentityRepository;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.security.config.SecurityProperties;
import com.sup2i.food.security.exception.AccountBlockedException;
import com.sup2i.food.security.exception.AccountSuspendedException;
import com.sup2i.food.security.exception.LoginRateLimitedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;

@Service
public class LocalAuthenticationService {

    private final AuthIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuthLoginAuditService auditService;
    private final LoginRateLimitService rateLimitService;
    private final SecurityProperties properties;

    public LocalAuthenticationService(
        AuthIdentityRepository identityRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        RefreshTokenService refreshTokenService,
        AuthLoginAuditService auditService,
        LoginRateLimitService rateLimitService,
        SecurityProperties properties
    ) {
        this.identityRepository =
            identityRepository;

        this.userRepository =
            userRepository;

        this.passwordEncoder =
            passwordEncoder;

        this.refreshTokenService =
            refreshTokenService;

        this.auditService =
            auditService;

        this.rateLimitService =
            rateLimitService;

        this.properties =
            properties;
    }

    @Transactional
    public AuthenticationTokens login(
        String loginIdentifier,
        String password,
        String deviceInfo,
        InetAddress ipAddress,
        String userAgent
    ) {
        String identifier =
            loginIdentifier.trim();

        AuthIdentity identity =
            identityRepository
                .findByProviderTypeAndProviderCodeAndLoginIdentifierAndActiveTrue(
                    AuthProviderType.LOCAL,
                    properties.localProviderCode(),
                    identifier
                )
                .orElse(null);

        /*
         * Identifiant inconnu.
         *
         * Même réponse "Invalid credentials" afin de ne pas
         * confirmer directement l'existence du compte.
         */
        if (identity == null) {

            enforceRateLimit(
                null,
                identifier,
                ipAddress,
                userAgent
            );

            auditService.recordFailure(
                null,
                identifier,
                "INVALID_CREDENTIALS",
                ipAddress,
                userAgent
            );

            throw new BadCredentialsException(
                "Invalid credentials."
            );
        }

        User user =
            identity.getUser();

        enforceAccountStatus(
            user,
            identifier,
            ipAddress,
            userAgent
        );

        enforceRateLimit(
            user,
            identifier,
            ipAddress,
            userAgent
        );

        String passwordHash =
            identity.getPasswordHash();

        if (
            passwordHash == null
            || !passwordEncoder.matches(
                password,
                passwordHash
            )
        ) {

            auditService.recordFailure(
                user.getId(),
                identifier,
                "INVALID_CREDENTIALS",
                ipAddress,
                userAgent
            );

            throw new BadCredentialsException(
                "Invalid credentials."
            );
        }

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
            identifier,
            ipAddress,
            userAgent
        );

        return tokens;
    }

    private void enforceAccountStatus(
        User user,
        String identifier,
        InetAddress ipAddress,
        String userAgent
    ) {
        UserStatus status =
            user.getStatus();

        if (status == UserStatus.ACTIVE) {
            return;
        }

        if (status == UserStatus.SUSPENDED) {

            auditService.recordBlocked(
                user.getId(),
                identifier,
                "ACCOUNT_SUSPENDED",
                ipAddress,
                userAgent
            );

            throw new AccountSuspendedException(
                "User account is suspended."
            );
        }

        /*
         * BLOCKED et ARCHIVED sont volontairement refusés.
         * On ne crée pas un nouveau code API ARCHIVED ici
         * puisque ce code n'est pas défini dans le contrat
         * que nous avons validé.
         */
        auditService.recordBlocked(
            user.getId(),
            identifier,
            "ACCOUNT_BLOCKED",
            ipAddress,
            userAgent
        );

        throw new AccountBlockedException(
            "User account is blocked."
        );
    }

    private void enforceRateLimit(
        User user,
        String identifier,
        InetAddress ipAddress,
        String userAgent
    ) {
        if (
            !rateLimitService.isRateLimited(
                identifier,
                ipAddress
            )
        ) {
            return;
        }

        auditService.recordBlocked(
            user == null
                ? null
                : user.getId(),
            identifier,
            "LOGIN_RATE_LIMITED",
            ipAddress,
            userAgent
        );

        throw new LoginRateLimitedException(
            "Too many failed login attempts. Try again later."
        );
    }
}