package com.sup2i.food.kitchen.service.routing;

import java.util.List;

public record KitchenRoutingPlan(
    List<KitchenTicketPlan> tickets
) {

    public KitchenRoutingPlan {

        if (
            tickets == null
            || tickets.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Kitchen routing plan must contain at least one ticket."
            );
        }

        tickets =
            List.copyOf(
                tickets
            );
    }
}
