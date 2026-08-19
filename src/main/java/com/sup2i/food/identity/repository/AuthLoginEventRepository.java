package com.sup2i.food.identity.repository;

import com.sup2i.food.identity.domain.AuthLoginEvent;
import com.sup2i.food.identity.domain.AuthLoginResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuthLoginEventRepository
    extends JpaRepository<AuthLoginEvent, UUID> {

    long countByIdentifierHashAndResultAndOccurredAtGreaterThanEqual(
        String identifierHash,
        AuthLoginResult result,
        OffsetDateTime occurredAt
    );

    long countByIdentifierHashAndIpAddressAndResultAndOccurredAtGreaterThanEqual(
        String identifierHash,
        InetAddress ipAddress,
        AuthLoginResult result,
        OffsetDateTime occurredAt
    );
}