package com.sup2i.food.security.service;

import com.sup2i.food.audit.service.AuditLogService;
import com.sup2i.food.identity.domain.AuthIdentity;
import com.sup2i.food.identity.domain.AuthProviderType;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.AuthIdentityRepository;
import com.sup2i.food.security.config.SecurityProperties;
import com.sup2i.food.security.domain.PasswordResetToken;
import com.sup2i.food.security.api.dto.ForgotPasswordResponse;
import com.sup2i.food.security.exception.PasswordResetTokenInvalidException;
import com.sup2i.food.security.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final Logger
        log =
            LoggerFactory.getLogger(
                PasswordResetService.class
            );

    private static final int
        RESET_TOKEN_TTL_MINUTES =
            30;

    private static final String
        GENERIC_REQUEST_MESSAGE =
            "If an account exists for this email, a reset link has been sent.";

    private static final String
        GENERIC_TOKEN_ERROR =
            "Reset token is invalid or has expired.";

    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final TokenHashService tokenHashService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final PasswordPolicyService passwordPolicyService;
    private final AuditLogService auditLogService;
    private final SecurityProperties properties;
    private final Environment environment;

    public PasswordResetService(
        AuthIdentityRepository authIdentityRepository,
        PasswordResetTokenRepository passwordResetTokenRepository,
        TokenHashService tokenHashService,
        PasswordEncoder passwordEncoder,
        RefreshTokenService refreshTokenService,
        PasswordPolicyService passwordPolicyService,
        AuditLogService auditLogService,
        SecurityProperties properties,
        Environment environment
    ) {
        this.authIdentityRepository =
            authIdentityRepository;

        this.passwordResetTokenRepository =
            passwordResetTokenRepository;

        this.tokenHashService =
            tokenHashService;

        this.passwordEncoder =
            passwordEncoder;

        this.refreshTokenService =
            refreshTokenService;

        this.passwordPolicyService =
            passwordPolicyService;

        this.auditLogService =
            auditLogService;

        this.properties =
            properties;

        this.environment =
            environment;
    }

    @Transactional
    public ForgotPasswordResponse requestReset(
        String email
    ) {

        String identifier =
            email.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        Optional<AuthIdentity> identityOptional =
            authIdentityRepository
                .findByProviderTypeAndProviderCodeAndLoginIdentifierAndActiveTrue(
                    AuthProviderType.LOCAL,
                    properties.localProviderCode(),
                    identifier
                );

        if (identityOptional.isEmpty()) {

            log.info(
                "Password reset requested for unknown identifier."
            );

            return new ForgotPasswordResponse(
                GENERIC_REQUEST_MESSAGE,
                devToken(null)
            );
        }

        User user =
            identityOptional.get()
                .getUser();

        OffsetDateTime now =
            OffsetDateTime.now();

        passwordResetTokenRepository
            .findAllByUserIdAndRevokedAtIsNullAndUsedAtIsNull(
                user.getId()
            )
            .forEach(token ->
                token.revoke(now)
            );

        TokenHashService.GeneratedToken generated =
            tokenHashService.generate();

        passwordResetTokenRepository
            .save(
                new PasswordResetToken(
                    user,
                    generated.tokenHash(),
                    now.plusMinutes(
                        RESET_TOKEN_TTL_MINUTES
                    )
                )
            );

        auditLogService.record(
            user.getOrganization().getId(),
            null,
            "PASSWORD_RESET_REQUESTED",
            "USER",
            user.getId(),
            java.util.Map.of(),
            java.util.Map.of(
                "requested",
                Boolean.TRUE
            ),
            null
        );

        log.info(
            "Password reset requested for user {}.",
            user.getId()
        );

        return new ForgotPasswordResponse(
            GENERIC_REQUEST_MESSAGE,
            devToken(
                generated.rawToken()
            )
        );
    }

    @Transactional
    public void resetPassword(
        String rawToken,
        String newPassword
    ) {

        passwordPolicyService.enforce(
            newPassword
        );

        String hash =
            tokenHashService.hash(
                rawToken
            );

        PasswordResetToken token =
            passwordResetTokenRepository
                .findByTokenHash(hash)
                .orElseThrow(() ->
                    new PasswordResetTokenInvalidException(
                        GENERIC_TOKEN_ERROR
                    )
                );

        OffsetDateTime now =
            OffsetDateTime.now();

        if (
            token.isUsed()
            || token.isRevoked()
            || !token.getExpiresAt()
                .isAfter(now)
        ) {
            throw new PasswordResetTokenInvalidException(
                GENERIC_TOKEN_ERROR
            );
        }

        User user =
            token.getUser();

        AuthIdentity identity =
            authIdentityRepository
                .findAllByUserIdAndActiveTrue(
                    user.getId()
                )
                .stream()
                .filter(candidate ->
                    candidate.getProviderType()
                        == AuthProviderType.LOCAL
                )
                .findFirst()
                .orElseThrow(() ->
                    new PasswordResetTokenInvalidException(
                        GENERIC_TOKEN_ERROR
                    )
                );

        identity.setPasswordHash(
            passwordEncoder.encode(
                newPassword
            )
        );

        List<PasswordResetToken> siblings =
            passwordResetTokenRepository
                .findAllByUserIdAndRevokedAtIsNullAndUsedAtIsNull(
                    user.getId()
                );

        for (
            PasswordResetToken sibling
            : siblings
        ) {

            if (
                !sibling.getId()
                    .equals(
                        token.getId()
                    )
            ) {
                sibling.revoke(now);
            }
        }

        token.markUsed(now);

        auditLogService.record(
            user.getOrganization().getId(),
            null,
            "PASSWORD_RESET_COMPLETED",
            "USER",
            user.getId(),
            java.util.Map.of(),
            java.util.Map.of(
                "completed",
                Boolean.TRUE
            ),
            null
        );

        refreshTokenService
            .revokeAllForUser(
                user.getId()
            );
    }

    private String devToken(
        String rawToken
    ) {

        return environment.matchesProfiles(
                "test",
                "dev"
            )
                ? rawToken
                : null;
    }
}
