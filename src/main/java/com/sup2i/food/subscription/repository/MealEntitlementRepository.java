package com.sup2i.food.subscription.repository;

import com.sup2i.food.common.domain.MealType;
import com.sup2i.food.subscription.domain.MealEntitlement;
import com.sup2i.food.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MealEntitlementRepository
    extends JpaRepository<MealEntitlement, UUID> {

    List<MealEntitlement>
        findAllBySubscription_IdOrderByMealTypeAsc(
            UUID subscriptionId
        );

    List<MealEntitlement>
        findAllBySubscription_Student_IdAndSubscription_StatusAndMealType(
            UUID studentId,
            SubscriptionStatus status,
            MealType mealType
        );
}
