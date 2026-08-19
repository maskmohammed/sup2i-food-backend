package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.security.config.SecurityProperties;
import com.sup2i.food.security.domain.RefreshToken;
import com.sup2i.food.security.repository.RefreshTokenRepository;
import com.sup2i.food.security.service.AuthorizationSnapshotService.AuthorizationSnapshot;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final TokenHashService tokenHashService;
    private final AuthorizationSnapshotService authorizationService;
    private final JwtService jwtService;
    private final SecurityProperties properties;

    public RefreshTokenService(
        RefreshTokenRepository repository,
        TokenHashService tokenHashService,
        AuthorizationSnapshotService authorizationService,
        JwtService jwtService,
        SecurityProperties properties
    ) {
        this.repository = repository;
        this.tokenHashService = tokenHashService;
        this.authorizationService = authorizationService;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public AuthenticationTokens issue(
        User user,
        String deviceInfo,
        InetAddress ipAddress
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        TokenHashService.GeneratedToken generated =
            tokenHashService.generate();

        RefreshToken refreshToken =
            new RefreshToken(
                user,
                generated.tokenHash(),
                now.plus(properties.refreshTokenTtl())
            );

        refreshToken.setDeviceInfo(deviceInfo);
        refreshToken.setIpAddress(ipAddress);

        refreshToken = repository.save(refreshToken);

        AuthorizationSnapshot authorization =
            authorizationService.load(user.getId());

        JwtService.AccessToken access =
            jwtService.issueAccessToken(
                user,
                authorization,
                refreshToken.getId()
            );

        return new AuthenticationTokens(
            user.getId(),
            access.token(),
            access.expiresAt(),
            generated.rawToken()
        );
    }

    @Transactional(
        noRollbackFor = {
            BadCredentialsException.class,
            CredentialsExpiredException.class
        }
    )
    public AuthenticationTokens rotate(
        String rawRefreshToken,
        String deviceInfo,
        InetAddress ipAddress
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        String hash =
            tokenHashService.hash(rawRefreshToken);

        RefreshToken current =
            repository
                .findByTokenHashForUpdate(hash)
                .orElseThrow(() ->
                    new BadCredentialsException(
                        "Invalid refresh token."
                    )
                );

        /*
         * Token déjà révoqué + remplacé :
         * probable réutilisation d'un ancien refresh token.
         *
         * On révoque toutes les sessions actives de l'utilisateur.
         */
        if (current.isRevoked()) {

            if (current.getReplacedBy() != null) {
                revokeAll(
                    current.getUser(),
                    now
                );
            }

            throw new BadCredentialsException(
                "Refresh token reuse detected."
            );
        }

        if (!current.getExpiresAt().isAfter(now)) {

            current.revoke(now);

            throw new CredentialsExpiredException(
                "Refresh token has expired."
            );
        }

        TokenHashService.GeneratedToken generated =
            tokenHashService.generate();

        RefreshToken replacement =
            new RefreshToken(
                current.getUser(),
                generated.tokenHash(),
                now.plus(properties.refreshTokenTtl())
            );

        replacement.setDeviceInfo(deviceInfo);
        replacement.setIpAddress(ipAddress);

        replacement = repository.save(replacement);

        current.replaceWith(
            replacement,
            now
        );

        AuthorizationSnapshot authorization =
            authorizationService.load(
                current.getUser().getId()
            );

        JwtService.AccessToken access =
            jwtService.issueAccessToken(
                current.getUser(),
                authorization,
                replacement.getId()
            );

        return new AuthenticationTokens(
            current.getUser().getId(),
            access.token(),
            access.expiresAt(),
            generated.rawToken()
        );
    }

    @Transactional
    public void revoke(
        String rawRefreshToken
    ) {
        String hash =
            tokenHashService.hash(rawRefreshToken);

        repository
            .findByTokenHashForUpdate(hash)
            .ifPresent(token -> {
                if (!token.isRevoked()) {
                    token.revoke(
                        OffsetDateTime.now()
                    );
                }
            });
    }

    @Transactional
    public void revokeSession(
        UUID sessionId,
        UUID userId
    ) {
        repository
            .findByIdAndUserIdForUpdate(
                sessionId,
                userId
            )
            .ifPresent(token -> {
                if (!token.isRevoked()) {
                    token.revoke(
                        OffsetDateTime.now()
                    );
                }
            });
    }

    private void revokeAll(
        User user,
        OffsetDateTime revokedAt
    ) {
        List<RefreshToken> tokens =
            repository
                .findAllByUserIdAndRevokedAtIsNull(
                    user.getId()
                );

        tokens.forEach(
            token -> token.revoke(revokedAt)
        );
    }
}