package com.sup2i.food.security.repository;

import com.sup2i.food.security.domain.MfaMethodStatus;
import com.sup2i.food.security.domain.MfaMethodType;
import com.sup2i.food.security.domain.UserMfaMethod;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserMfaMethodRepository
    extends JpaRepository<UserMfaMethod, UUID> {

    List<UserMfaMethod>
        findAllByUserId(
            UUID userId
        );

    Optional<UserMfaMethod>
        findFirstByUserIdAndStatusAndPrimaryTrue(
            UUID userId,
            MfaMethodStatus status
        );

    List<UserMfaMethod>
        findAllByUserIdAndMethodTypeAndStatus(
            UUID userId,
            MfaMethodType methodType,
            MfaMethodStatus status
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select method
        from UserMfaMethod method
        join fetch method.user user
        where user.id = :userId
          and method.status = :status
          and method.primary = true
        """)
    Optional<UserMfaMethod>
        findPrimaryForUpdate(
            @Param("userId")
            UUID userId,

            @Param("status")
            MfaMethodStatus status
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select method
        from UserMfaMethod method
        join fetch method.user user
        where method.id = :methodId
          and user.id = :userId
        """)
    Optional<UserMfaMethod>
        findByIdAndUserIdForUpdate(
            @Param("methodId")
            UUID methodId,

            @Param("userId")
            UUID userId
        );
}