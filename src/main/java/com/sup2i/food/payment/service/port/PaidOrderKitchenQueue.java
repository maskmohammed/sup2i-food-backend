package com.sup2i.food.payment.service.port;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface PaidOrderKitchenQueue {

    void enqueuePaidOrder(
        UUID organizationId,
        UUID orderId,
        OffsetDateTime at
    );
}
