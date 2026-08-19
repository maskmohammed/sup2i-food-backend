package com.sup2i.food.security.repository;

import com.sup2i.food.security.domain.UserMfaRecoveryCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserMfaRecoveryCodeRepository
    extends JpaRepository<
        UserMfaRecoveryCode,
        UUID
    > {

    Optional<UserMfaRecoveryCode>
        findByCodeHash(
            String codeHash
        );

    List<UserMfaRecoveryCode>
        findAllByMfaMethodId(
            UUID mfaMethodId
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select recovery
        from UserMfaRecoveryCode recovery
        join fetch recovery.mfaMethod method
        join fetch method.user user
        where recovery.codeHash = :codeHash
          and method.id = :methodId
          and user.id = :userId
        """)
    Optional<UserMfaRecoveryCode>
        findForUpdate(
            @Param("codeHash")
            String codeHash,

            @Param("methodId")
            UUID methodId,

            @Param("userId")
            UUID userId
        );
}