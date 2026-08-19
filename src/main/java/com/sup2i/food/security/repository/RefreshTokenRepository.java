package com.sup2i.food.security.repository;

import com.sup2i.food.security.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository
    extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(
        String tokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select rt
        from RefreshToken rt
        join fetch rt.user u
        where rt.tokenHash = :tokenHash
        """)
    Optional<RefreshToken> findByTokenHashForUpdate(
        @Param("tokenHash") String tokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select rt
        from RefreshToken rt
        join fetch rt.user u
        where rt.id = :tokenId
          and u.id = :userId
        """)
    Optional<RefreshToken> findByIdAndUserIdForUpdate(
        @Param("tokenId") UUID tokenId,
        @Param("userId") UUID userId
    );

    @Query("""
        select rt
        from RefreshToken rt
        join fetch rt.user u
        where rt.id = :tokenId
          and u.id = :userId
        """)
    Optional<RefreshToken> findByIdAndUserId(
        @Param("tokenId") UUID tokenId,
        @Param("userId") UUID userId
    );

    List<RefreshToken>
        findAllByUserIdAndRevokedAtIsNull(
            UUID userId
        );
}