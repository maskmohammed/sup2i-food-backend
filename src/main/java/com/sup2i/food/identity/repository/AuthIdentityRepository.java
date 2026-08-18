package com.sup2i.food.identity.repository;

import com.sup2i.food.identity.domain.AuthIdentity;
import com.sup2i.food.identity.domain.AuthProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthIdentityRepository
    extends JpaRepository<AuthIdentity, UUID> {

    Optional<AuthIdentity>
        findByProviderCodeAndLoginIdentifierAndActiveTrue(
            String providerCode,
            String loginIdentifier
        );

    Optional<AuthIdentity>
        findByProviderTypeAndProviderCodeAndLoginIdentifierAndActiveTrue(
            AuthProviderType providerType,
            String providerCode,
            String loginIdentifier
        );

    List<AuthIdentity>
        findAllByUserIdAndActiveTrue(UUID userId);
}