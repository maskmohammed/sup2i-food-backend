package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.security.domain.MfaMethodStatus;
import com.sup2i.food.security.domain.MfaMethodType;
import com.sup2i.food.security.domain.UserMfaMethod;
import com.sup2i.food.security.exception.MfaRequiredException;
import com.sup2i.food.security.exception.MfaSetupRequiredException;
import com.sup2i.food.security.repository.UserMfaMethodRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;

@Service
public class MfaVerificationService {

    private final MfaPolicyService policyService;
    private final UserMfaMethodRepository methodRepository;
    private final MfaSecretCryptoService cryptoService;
    private final TotpService totpService;
    private final MfaRecoveryCodeService recoveryCodeService;
    private final AuthLoginAuditService auditService;

    public MfaVerificationService(
        MfaPolicyService policyService,
        UserMfaMethodRepository methodRepository,
        MfaSecretCryptoService cryptoService,
        TotpService totpService,
        MfaRecoveryCodeService recoveryCodeService,
        AuthLoginAuditService auditService
    ) {
        this.policyService = policyService;
        this.methodRepository =
            methodRepository;
        this.cryptoService =
            cryptoService;
        this.totpService = totpService;
        this.recoveryCodeService =
            recoveryCodeService;
        this.auditService =
            auditService;
    }

    @Transactional
    public void enforce(
        User user,
        String identifier,
        String totpCode,
        String recoveryCode,
        InetAddress ipAddress,
        String userAgent
    ) {
        if (
            !policyService.mustUseMfa(
                user.getId()
            )
        ) {
            return;
        }

        UserMfaMethod method =
            methodRepository
                .findPrimaryForUpdate(
                    user.getId(),
                    MfaMethodStatus.ACTIVE
                )
                .orElse(null);

        if (method == null) {

            auditService.recordBlocked(
                user.getId(),
                identifier,
                "MFA_SETUP_REQUIRED",
                ipAddress,
                userAgent
            );

            throw new MfaSetupRequiredException(
                "Multi-factor authentication setup is required."
            );
        }

        boolean hasTotp =
            totpCode != null
                && !totpCode.isBlank();

        boolean hasRecovery =
            recoveryCode != null
                && !recoveryCode.isBlank();

        if (
            !hasTotp
            && !hasRecovery
        ) {
            auditService.recordBlocked(
                user.getId(),
                identifier,
                "MFA_REQUIRED",
                ipAddress,
                userAgent
            );

            throw new MfaRequiredException(
                "Multi-factor authentication is required."
            );
        }

        if (
            hasTotp
            && hasRecovery
        ) {
            fail(
                user,
                identifier,
                "INVALID_MFA_REQUEST",
                ipAddress,
                userAgent
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        boolean valid;

        if (hasRecovery) {

            valid =
                recoveryCodeService
                    .consume(
                        user.getId(),
                        method.getId(),
                        recoveryCode,
                        now
                    );

        } else {

            if (
                method.getMethodType()
                    != MfaMethodType.TOTP
            ) {
                valid = false;

            } else {

                byte[] secret =
                    cryptoService.decrypt(
                        method
                            .getSecretCiphertext(),
                        user.getId(),
                        method.getMethodType()
                    );

                valid =
                    totpService.verify(
                        secret,
                        totpCode,
                        method.getLastUsedAt()
                    );
            }
        }

        if (!valid) {
            fail(
                user,
                identifier,
                "INVALID_MFA_CODE",
                ipAddress,
                userAgent
            );
        }

        method.markUsed(now);

        methodRepository.save(method);
    }

    private void fail(
        User user,
        String identifier,
        String reason,
        InetAddress ipAddress,
        String userAgent
    ) {
        auditService.recordFailure(
            user.getId(),
            identifier,
            reason,
            ipAddress,
            userAgent
        );

        throw new BadCredentialsException(
            "Invalid MFA credential."
        );
    }
}