package com.sup2i.food.security.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;

@Service
public class LocalAuthenticationService {

    private final LocalCredentialService credentialService;
    private final MfaVerificationService mfaVerificationService;
    private final AuthenticationCompletionService completionService;

    public LocalAuthenticationService(
        LocalCredentialService credentialService,
        MfaVerificationService mfaVerificationService,
        AuthenticationCompletionService completionService
    ) {
        this.credentialService =
            credentialService;
        this.mfaVerificationService =
            mfaVerificationService;
        this.completionService =
            completionService;
    }

    @Transactional
    public AuthenticationTokens login(
        String loginIdentifier,
        String password,
        String mfaCode,
        String recoveryCode,
        String deviceInfo,
        InetAddress ipAddress,
        String userAgent
    ) {
        LocalCredentialService.VerifiedLocalIdentity verified =
            credentialService.verify(
                loginIdentifier,
                password,
                ipAddress,
                userAgent
            );

        mfaVerificationService.enforce(
            verified.user(),
            verified.identifier(),
            mfaCode,
            recoveryCode,
            ipAddress,
            userAgent
        );

        return completionService.complete(
            verified,
            deviceInfo,
            ipAddress,
            userAgent
        );
    }
}