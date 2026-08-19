package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.AuthLoginResult;
import com.sup2i.food.identity.repository.AuthLoginEventRepository;
import com.sup2i.food.security.config.SecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;

@Service
public class LoginRateLimitService {

    private final AuthLoginEventRepository repository;
    private final TokenHashService tokenHashService;
    private final SecurityProperties properties;

    public LoginRateLimitService(
        AuthLoginEventRepository repository,
        TokenHashService tokenHashService,
        SecurityProperties properties
    ) {
        this.repository = repository;
        this.tokenHashService = tokenHashService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public boolean isRateLimited(
        String identifier,
        InetAddress ipAddress
    ) {
        SecurityProperties.LoginProtection config =
            properties.loginProtection();

        if (
            config == null
            || !config.enabled()
        ) {
            return false;
        }

        if (
            config.maxFailedAttempts() <= 0
            || config.failureWindow() == null
            || config.failureWindow().isZero()
            || config.failureWindow().isNegative()
        ) {
            return false;
        }

        String identifierHash =
            tokenHashService.hash(identifier);

        OffsetDateTime since =
            OffsetDateTime.now()
                .minus(config.failureWindow());

        long failures;

        if (ipAddress != null) {

            failures =
                repository
                    .countByIdentifierHashAndIpAddressAndResultAndOccurredAtGreaterThanEqual(
                        identifierHash,
                        ipAddress,
                        AuthLoginResult.FAILED,
                        since
                    );

        } else {

            failures =
                repository
                    .countByIdentifierHashAndResultAndOccurredAtGreaterThanEqual(
                        identifierHash,
                        AuthLoginResult.FAILED,
                        since
                    );
        }

        return failures
            >= config.maxFailedAttempts();
    }
}