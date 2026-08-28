package com.sup2i.food.subscription.repository;

import com.sup2i.food.subscription.domain.Subscription;
import com.sup2i.food.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository
    extends JpaRepository<Subscription, UUID> {

    List<Subscription>
        findAllByStudent_IdAndStatusIn(
            UUID studentId,
            List<SubscriptionStatus> statuses
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select s from Subscription s "
            + "where s.id = :id "
            + "and s.student.id = :studentId"
    )
    Optional<Subscription>
        findOwnedByIdForUpdate(
            @Param("id") UUID id,
            @Param("studentId") UUID studentId
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select s from Subscription s "
            + "where s.id = :id "
            + "and s.plan.organization.id = :organizationId"
    )
    Optional<Subscription>
        findByIdAndOrganizationForUpdate(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId
        );

    List<Subscription>
        findAllByStudent_IdOrderByCreatedAtDesc(
            UUID studentId
        );

    Page<Subscription>
        findAllByPlan_Organization_Id(
            UUID organizationId,
            Pageable pageable
        );

    Optional<Subscription>
        findByIdAndPlan_Organization_Id(
            UUID id,
            UUID organizationId
        );

    List<Subscription>
        findAllByStatusAndEndsAtBefore(
            com.sup2i.food.subscription.domain.SubscriptionStatus status,
            LocalDate date
        );
}
