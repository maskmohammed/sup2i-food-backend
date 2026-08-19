package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.AuthIdentity;
import com.sup2i.food.identity.domain.AuthLoginEvent;
import com.sup2i.food.identity.domain.AuthLoginResult;
import com.sup2i.food.identity.domain.AuthProviderType;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.domain.UserStatus;
import com.sup2i.food.identity.repository.AuthIdentityRepository;
import com.sup2i.food.identity.repository.AuthLoginEventRepository;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.security.config.SecurityProperties;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;

@Service
public class LocalAuthenticationService {

    private final AuthIdentityRepository identityRepository;
    private final AuthLoginEventRepository loginEventRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final TokenHashService tokenHashService;
    private final SecurityProperties properties;

    public LocalAuthenticationService(
        AuthIdentityRepository identityRepository,
        AuthLoginEventRepository loginEventRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        RefreshTokenService refreshTokenService,
        TokenHashService tokenHashService,
        SecurityProperties properties
    ) {
        this.identityRepository = identityRepository;
        this.loginEventRepository =
            loginEventRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService =
            refreshTokenService;
        this.tokenHashService = tokenHashService;
        this.properties = properties;
    }

    @Transactional(
        noRollbackFor = {
            BadCredentialsException.class,
            DisabledException.class
        }
    )
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
                .orElseThrow(() -> {
                    recordFailure(
                        null,
                        identifier,
                        "INVALID_CREDENTIALS",
                        ipAddress,
                        userAgent
                    );

                    return new BadCredentialsException(
                        "Invalid credentials."
                    );
                });

        User user = identity.getUser();

        if (user.getStatus() != UserStatus.ACTIVE) {

            recordBlocked(
                user,
                identifier,
                ipAddress,
                userAgent
            );

            throw new DisabledException(
                "User account is not active."
            );
        }

        String passwordHash =
            identity.getPasswordHash();

        if (
            passwordHash == null
            || !passwordEncoder.matches(
                password,
                passwordHash
            )
        ) {
            recordFailure(
                user,
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

        recordSuccess(
            user,
            identifier,
            ipAddress,
            userAgent
        );

        return refreshTokenService.issue(
            user,
            deviceInfo,
            ipAddress
        );
    }

    private void recordSuccess(
        User user,
        String identifier,
        InetAddress ipAddress,
        String userAgent
    ) {
        AuthLoginEvent event =
            new AuthLoginEvent(
                AuthLoginResult.SUCCESS
            );

        populateEvent(
            event,
            user,
            identifier,
            ipAddress,
            userAgent
        );

        loginEventRepository.save(event);
    }

    private void recordFailure(
        User user,
        String identifier,
        String reason,
        InetAddress ipAddress,
        String userAgent
    ) {
        AuthLoginEvent event =
            new AuthLoginEvent(
                AuthLoginResult.FAILED
            );

        populateEvent(
            event,
            user,
            identifier,
            ipAddress,
            userAgent
        );

        event.setFailureReason(reason);

        loginEventRepository.save(event);
    }

    private void recordBlocked(
        User user,
        String identifier,
        InetAddress ipAddress,
        String userAgent
    ) {
        AuthLoginEvent event =
            new AuthLoginEvent(
                AuthLoginResult.BLOCKED
            );

        populateEvent(
            event,
            user,
            identifier,
            ipAddress,
            userAgent
        );

        event.setFailureReason(
            "ACCOUNT_NOT_ACTIVE"
        );

        loginEventRepository.save(event);
    }

    private void populateEvent(
        AuthLoginEvent event,
        User user,
        String identifier,
        InetAddress ipAddress,
        String userAgent
    ) {
        event.setUser(user);
        event.setIdentifierHash(
            tokenHashService.hash(identifier)
        );
        event.setIpAddress(ipAddress);
        event.setUserAgent(userAgent);
    }
}