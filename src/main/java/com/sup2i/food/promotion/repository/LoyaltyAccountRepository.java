package com.sup2i.food.promotion.repository;

import com.sup2i.food.promotion.domain.LoyaltyAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoyaltyAccountRepository
    extends JpaRepository<LoyaltyAccount, UUID> {

    Optional<LoyaltyAccount>
        findByStudent_Id(
            UUID studentId
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a
        from LoyaltyAccount a
        where a.student.id = :studentId
        """)
    Optional<LoyaltyAccount> findByStudent_IdForUpdate(
        @Param("studentId")
        UUID studentId
    );
}