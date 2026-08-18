package com.sup2i.food.security.repository;

import com.sup2i.food.security.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository
    extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findAllByUserIdAndActiveTrue(UUID userId);
}
