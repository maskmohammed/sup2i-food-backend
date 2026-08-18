package com.sup2i.food.security.repository;

import com.sup2i.food.security.domain.UserMfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserMfaRecoveryCodeRepository
    extends JpaRepository<UserMfaRecoveryCode, UUID> {

    Optional<UserMfaRecoveryCode> findByCodeHash(String codeHash);

    List<UserMfaRecoveryCode> findAllByMfaMethodId(UUID mfaMethodId);
}
