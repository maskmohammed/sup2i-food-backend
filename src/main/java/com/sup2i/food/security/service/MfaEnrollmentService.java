package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.security.config.SecurityProperties;
import com.sup2i.food.security.domain.MfaMethodStatus;
import com.sup2i.food.security.domain.MfaMethodType;
import com.sup2i.food.security.domain.UserMfaMethod;
import com.sup2i.food.security.exception.MfaAlreadyConfiguredException;
import com.sup2i.food.security.repository.UserMfaMethodRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MfaEnrollmentService {

    private final LocalCredentialService credentialService;
    private final UserMfaMethodRepository methodRepository;
    private final MfaSecretCryptoService cryptoService;
    private final TotpService totpService;
    private final MfaRecoveryCodeService recoveryCodeService;
    private final AuthenticationCompletionService completionService;
    private final SecurityProperties properties;

    private final SecureRandom secureRandom =
        new SecureRandom();

    public MfaEnrollmentService(
        LocalCredentialService credentialService,
        UserMfaMethodRepository methodRepository,
        MfaSecretCryptoService cryptoService,
        TotpService totpService,
        MfaRecoveryCodeService recoveryCodeService,
        AuthenticationCompletionService completionService,
        SecurityProperties properties
    ) {
        this.credentialService =
            credentialService;
        this.methodRepository =
            methodRepository;
        this.cryptoService =
            cryptoService;
        this.totpService =
            totpService;
        this.recoveryCodeService =
            recoveryCodeService;
        this.completionService =
            completionService;
        this.properties =
            properties;
    }

    @Transactional
    public SetupResult start(
        String email,
        String password,
        String label,
        InetAddress ipAddress,
        String userAgent
    ) {
        LocalCredentialService.VerifiedLocalIdentity verified =
            credentialService.verify(
                email,
                password,
                ipAddress,
                userAgent
            );

        User user =
            verified.user();

        boolean alreadyConfigured =
            methodRepository
                .findFirstByUserIdAndStatusAndPrimaryTrue(
                    user.getId(),
                    MfaMethodStatus.ACTIVE
                )
                .isPresent();

        if (alreadyConfigured) {
            throw new MfaAlreadyConfiguredException(
                "MFA is already configured."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        List<UserMfaMethod> pending =
            methodRepository
                .findAllByUserIdAndMethodTypeAndStatus(
                    user.getId(),
                    MfaMethodType.TOTP,
                    MfaMethodStatus.PENDING
                );

        pending.forEach(
            method ->
                method.revoke(now)
        );

        methodRepository.saveAll(
            pending
        );

        byte[] secret =
            new byte[20];

        secureRandom.nextBytes(secret);

        UserMfaMethod method =
            new UserMfaMethod(
                user,
                MfaMethodType.TOTP
            );

        method.setLabel(
            label == null
                || label.isBlank()
                ? "Authenticator"
                : label.trim()
        );

        method.setSecretCiphertext(
            cryptoService.encrypt(
                secret,
                user.getId(),
                MfaMethodType.TOTP
            )
        );

        method =
            methodRepository.save(
                method
            );

        String base32 =
            Base32Codec.encode(
                secret
            );

        String issuer =
            "SUP2I FOOD";

        String account =
            user.getEmail();

        String uri =
            "otpauth://totp/"
                + url(issuer)
                + ":"
                + url(account)
                + "?secret="
                + base32
                + "&issuer="
                + url(issuer)
                + "&algorithm=SHA1"
                + "&digits="
                + TotpService.DIGITS
                + "&period="
                + TotpService.PERIOD_SECONDS;

        return new SetupResult(
            method.getId(),
            base32,
            uri
        );
    }

    @Transactional
    public ConfirmationResult confirm(
        String email,
        String password,
        UUID methodId,
        String code,
        String deviceInfo,
        InetAddress ipAddress,
        String userAgent
    ) {
        LocalCredentialService.VerifiedLocalIdentity verified =
            credentialService.verify(
                email,
                password,
                ipAddress,
                userAgent
            );

        User user =
            verified.user();

        UserMfaMethod method =
            methodRepository
                .findByIdAndUserIdForUpdate(
                    methodId,
                    user.getId()
                )
                .orElseThrow(() ->
                    new BadCredentialsException(
                        "Invalid MFA enrollment."
                    )
                );

        if (
            method.getMethodType()
                != MfaMethodType.TOTP
            || method.getStatus()
                != MfaMethodStatus.PENDING
        ) {
            throw new BadCredentialsException(
                "Invalid MFA enrollment."
            );
        }

        byte[] secret =
            cryptoService.decrypt(
                method.getSecretCiphertext(),
                user.getId(),
                MfaMethodType.TOTP
            );

        if (
            !totpService.verify(
                secret,
                code,
                null
            )
        ) {
            throw new BadCredentialsException(
                "Invalid MFA code."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        method.activate(now);
        method.setPrimary(true);
        method.markUsed(now);

        methodRepository.save(method);

        int recoveryCount =
            properties.mfa()
                .recoveryCodeCount();

        if (recoveryCount <= 0) {
            recoveryCount = 10;
        }

        List<String> recoveryCodes =
            recoveryCodeService.generate(
                method,
                recoveryCount
            );

        AuthenticationTokens tokens =
            completionService.complete(
                verified,
                deviceInfo,
                ipAddress,
                userAgent
            );

        return new ConfirmationResult(
            tokens,
            recoveryCodes
        );
    }

    private String url(
        String value
    ) {
        return URLEncoder
            .encode(
                value,
                StandardCharsets.UTF_8
            )
            .replace(
                "+",
                "%20"
            );
    }

    public record SetupResult(
        UUID methodId,
        String secret,
        String otpauthUri
    ) {
    }

    public record ConfirmationResult(
        AuthenticationTokens tokens,
        List<String> recoveryCodes
    ) {
    }
}