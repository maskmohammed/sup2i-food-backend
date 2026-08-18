package com.sup2i.food.security.service;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.security.config.SecurityProperties;
import com.sup2i.food.security.service.AuthorizationSnapshotService.AuthorizationSnapshot;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public JwtService(
        JwtEncoder jwtEncoder,
        SecurityProperties properties
    ) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public AccessToken issueAccessToken(
        User user,
        AuthorizationSnapshot authorization
    ) {
        Instant issuedAt = Instant.now();

        Instant expiresAt = issuedAt.plus(
            properties.jwt().accessTokenTtl()
        );

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(properties.jwt().issuer())
            .subject(user.getId().toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())

            .claim("email", user.getEmail())

            .claim(
                "roles",
                authorization.globalRoles()
                    .stream()
                    .sorted()
                    .toList()
            )

            .claim(
                "permissions",
                authorization.globalPermissions()
                    .stream()
                    .sorted()
                    .toList()
            )

            .claim(
                "role_scopes",
                authorization.roleScopes()
            )

            .build();

        String token = jwtEncoder
            .encode(
                JwtEncoderParameters.from(claims)
            )
            .getTokenValue();

        return new AccessToken(
            token,
            expiresAt
        );
    }

    public record AccessToken(
        String token,
        Instant expiresAt
    ) {
    }
}