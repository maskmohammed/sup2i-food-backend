package com.sup2i.food.subscription.repository;

import com.sup2i.food.subscription.domain.MealUsage;
import com.sup2i.food.subscription.domain.MealUsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MealUsageRepository
    extends JpaRepository<MealUsage, UUID> {

    long countByStudent_IdAndUsageDateAndMealTypeAndStatus(
        UUID studentId,
        java.time.LocalDate usageDate,
        com.sup2i.food.common.domain.MealType mealType,
        MealUsageStatus status
    );

    List<MealUsage>
        findAllByEntitlement_IdAndStatusOrderByUsageDateAsc(
            UUID entitlementId,
            MealUsageStatus status
        );
}