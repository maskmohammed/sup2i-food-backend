package com.sup2i.food.kitchen.integration;

import com.sup2i.food.kitchen.service.KitchenQueueService;
import com.sup2i.food.payment.service.port.PaidOrderKitchenQueue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class PaidOrderKitchenQueueAdapter
    implements PaidOrderKitchenQueue {

    private final KitchenQueueService kitchenQueueService;

    public PaidOrderKitchenQueueAdapter(
        KitchenQueueService kitchenQueueService
    ) {
        this.kitchenQueueService =
            kitchenQueueService;
    }

    @Override
    @Transactional(
        propagation = Propagation.MANDATORY
    )
    public void enqueuePaidOrder(
        UUID organizationId,
        UUID orderId,
        OffsetDateTime at
    ) {
        kitchenQueueService.queuePaidOrder(
            organizationId,
            orderId,
            at
        );
    }
}
