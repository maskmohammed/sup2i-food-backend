package com.sup2i.food.kitchen.service.routing;

import com.sup2i.food.kitchen.domain.PreparationRoute;
import com.sup2i.food.organization.domain.Location;

import java.util.List;

public record KitchenTicketPlan(
    Location kitchenLocation,
    PreparationRoute preparationRoute,
    int priority,
    List<KitchenRouteResolution> resolutions
) {

    public KitchenTicketPlan {

        if (kitchenLocation == null) {
            throw new IllegalArgumentException(
                "Kitchen location is required."
            );
        }

        if (priority < 0) {
            throw new IllegalArgumentException(
                "Kitchen ticket priority cannot be negative."
            );
        }

        if (
            resolutions == null
            || resolutions.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Kitchen ticket plan must contain at least one routing unit."
            );
        }

        resolutions =
            List.copyOf(
                resolutions
            );
    }
}
