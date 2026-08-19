package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.AuthIdentity;
import com.sup2i.food.identity.domain.AuthProviderType;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.domain.UserStatus;
import com.sup2i.food.identity.repository.AuthIdentityRepository;
import com.sup2i.food.security.config.SecurityProperties;
import com.sup2i.food.security.exception.AccountBlockedException;
import com.sup2i.food.security.exception.AccountSuspendedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.InetAddress;

@Service
public class LocalCredentialService {

    private final AuthIdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthLoginAuditService auditService;
    private final LoginRateLimitService rateLimitService;
    private final SecurityProperties properties;

    public LocalCredentialService(
        AuthIdentityRepository identityRepository,
        PasswordEncoder passwordEncoder,
        AuthLoginAuditService auditService,
        LoginRateLimitService rateLimitService,
        SecurityProperties properties
    ) {
        this.identityRepository =
            identityRepository;
        this.passwordEncoder =
            passwordEncoder;
        this.auditService =
            auditService;
        this.rateLimitService =
            rateLimitService;
        this.properties =
            properties;
    }

    public VerifiedLocalIdentity verify(
        String loginIdentifier,
        String password,
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

        enforceStatus(
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

        return new VerifiedLocalIdentity(
            identity,
            user,
            identifier
        );
    }

    private void enforceStatus(
        User user,
        String identifier,
        InetAddress ipAddress,
        String userAgent
    ) {
        if (
            user.getStatus()
                == UserStatus.ACTIVE
        ) {
            return;
        }

        if (
            user.getStatus()
                == UserStatus.SUSPENDED
        ) {
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

        throw new com.sup2i.food.security.exception.LoginRateLimitedException(
            "Too many failed login attempts. Try again later."
        );
    }

    public record VerifiedLocalIdentity(
        AuthIdentity identity,
        User user,
        String identifier
    ) {
    }
}