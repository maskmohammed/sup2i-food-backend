package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.security.api.dto.AuthResponse;
import com.sup2i.food.security.api.dto.UserSummaryResponse;
import com.sup2i.food.security.service.AuthorizationSnapshotService.AuthorizationSnapshot;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class AuthResponseService {

    private final UserRepository userRepository;
    private final AuthorizationSnapshotService authorizationService;

    public AuthResponseService(
        UserRepository userRepository,
        AuthorizationSnapshotService authorizationService
    ) {
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public AuthResponse create(
        AuthenticationTokens tokens
    ) {
        User user = userRepository
            .findById(tokens.userId())
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user no longer exists."
                )
            );

        AuthorizationSnapshot authorization =
            authorizationService.load(user.getId());

        long expiresIn = Math.max(
            0,
            Duration.between(
                Instant.now(),
                tokens.accessTokenExpiresAt()
            ).toSeconds()
        );

        return new AuthResponse(
            tokens.accessToken(),
            tokens.refreshToken(),
            expiresIn,
            new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus().name(),
                authorization.allRoles()
            )
        );
    }
}