package com.sup2i.food.kitchen.service.routing;

import com.sup2i.food.kitchen.domain.PreparationRoute;

public record KitchenRouteResolution(
    KitchenRoutingUnit unit,
    PreparationRoute route
) {

    public KitchenRouteResolution {

        if (unit == null) {
            throw new IllegalArgumentException(
                "Kitchen routing unit is required."
            );
        }

        if (route == null) {
            throw new IllegalArgumentException(
                "Preparation route is required."
            );
        }
    }
}
