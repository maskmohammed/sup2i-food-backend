package com.sup2i.food.subscription.repository;

import com.sup2i.food.subscription.domain.SubscriptionPlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanVersionRepository
    extends JpaRepository<SubscriptionPlanVersion, UUID> {

    Optional<SubscriptionPlanVersion>
        findByPlan_IdAndEffectiveToIsNull(
            UUID planId
        );

    Optional<SubscriptionPlanVersion>
        findByIdAndPlan_Id(
            UUID id,
            UUID planId
        );
}
