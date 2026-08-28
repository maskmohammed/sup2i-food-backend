package com.sup2i.food.subscription.repository;

import com.sup2i.food.subscription.domain.SubscriptionPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository
    extends JpaRepository<SubscriptionPlan, UUID> {

    boolean existsByOrganization_IdAndCode(
        UUID organizationId,
        String code
    );

    Page<SubscriptionPlan>
        findAllByOrganization_IdAndActiveTrue(
            UUID organizationId,
            Pageable pageable
        );

    Optional<SubscriptionPlan>
        findByIdAndOrganization_Id(
            UUID id,
            UUID organizationId
        );
}
