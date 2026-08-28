package com.sup2i.food.subscription.repository;

import com.sup2i.food.subscription.domain.SubscriptionPlanVersionService;
import com.sup2i.food.subscription.domain.SubscriptionPlanVersionServiceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubscriptionPlanVersionServiceRepository
    extends JpaRepository<
        SubscriptionPlanVersionService,
        SubscriptionPlanVersionServiceId
    > {

    List<SubscriptionPlanVersionService>
        findAllByPlanVersion_Id(
            UUID planVersionId
        );
}
