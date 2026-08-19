package com.sup2i.food.security.service;

import com.sup2i.food.security.config.SecurityProperties;
import com.sup2i.food.security.domain.MfaMethodStatus;
import com.sup2i.food.security.repository.UserMfaMethodRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class MfaPolicyService {

    private final SecurityProperties properties;
    private final AuthorizationSnapshotService authorizationService;
    private final UserMfaMethodRepository methodRepository;

    public MfaPolicyService(
        SecurityProperties properties,
        AuthorizationSnapshotService authorizationService,
        UserMfaMethodRepository methodRepository
    ) {
        this.properties = properties;
        this.authorizationService =
            authorizationService;
        this.methodRepository =
            methodRepository;
    }

    public boolean mustUseMfa(
        UUID userId
    ) {
        SecurityProperties.Mfa config =
            properties.mfa();

        if (
            config == null
            || !config.enabled()
        ) {
            return false;
        }

        /*
         * Un utilisateur ayant volontairement
         * configuré MFA continue à devoir l'utiliser,
         * même si son rôle sensible est ensuite retiré.
         */
        boolean configured =
            methodRepository
                .findFirstByUserIdAndStatusAndPrimaryTrue(
                    userId,
                    MfaMethodStatus.ACTIVE
                )
                .isPresent();

        if (configured) {
            return true;
        }

        Set<String> requiredRoles =
            config.requiredRoles();

        if (
            requiredRoles == null
            || requiredRoles.isEmpty()
        ) {
            return false;
        }

        return authorizationService
            .load(userId)
            .allRoles()
            .stream()
            .anyMatch(
                requiredRoles::contains
            );
    }

    public boolean roleRequiresMfa(
        UUID userId
    ) {
        SecurityProperties.Mfa config =
            properties.mfa();

        if (
            config == null
            || !config.enabled()
            || config.requiredRoles() == null
        ) {
            return false;
        }

        return authorizationService
            .load(userId)
            .allRoles()
            .stream()
            .anyMatch(
                config.requiredRoles()::contains
            );
    }
}