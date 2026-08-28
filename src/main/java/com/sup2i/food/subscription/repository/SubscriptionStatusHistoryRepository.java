package com.sup2i.food.subscription.repository;

import com.sup2i.food.subscription.domain.SubscriptionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubscriptionStatusHistoryRepository
    extends JpaRepository<SubscriptionStatusHistory, UUID> {

    List<SubscriptionStatusHistory>
        findAllBySubscription_IdOrderByCreatedAtAsc(
            UUID subscriptionId
        );
}
