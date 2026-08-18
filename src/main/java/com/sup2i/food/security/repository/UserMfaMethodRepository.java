package com.sup2i.food.security.repository;

import com.sup2i.food.security.domain.MfaMethodStatus;
import com.sup2i.food.security.domain.UserMfaMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserMfaMethodRepository
    extends JpaRepository<UserMfaMethod, UUID> {

    List<UserMfaMethod> findAllByUserId(UUID userId);

    Optional<UserMfaMethod>
        findFirstByUserIdAndStatusAndPrimaryTrue(
            UUID userId,
            MfaMethodStatus status
        );
}
