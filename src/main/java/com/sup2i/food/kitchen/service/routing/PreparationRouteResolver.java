package com.sup2i.food.kitchen.service.routing;

import com.sup2i.food.kitchen.domain.PreparationRoute;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class PreparationRouteResolver {

    public KitchenRouteResolution resolve(
        KitchenRoutingUnit unit,
        List<PreparationRoute> effectiveRoutes
    ) {
        if (unit == null) {
            throw new IllegalArgumentException(
                "Kitchen routing unit is required."
            );
        }

        if (
            effectiveRoutes == null
            || effectiveRoutes.isEmpty()
        ) {
            throw new IllegalStateException(
                "No effective preparation route exists."
            );
        }

        List<PreparationRoute> matching =
            new ArrayList<>();

        Integer maxPriority =
            null;

        for (
            PreparationRoute route
            : effectiveRoutes
        ) {
            if (
                !route.matchesScope(
                    unit.categoryId(),
                    unit.productId(),
                    unit.variantId()
                )
            ) {
                continue;
            }

            int priority =
                route.getPriority();

            if (
                maxPriority == null
                || priority > maxPriority
            ) {
                matching.clear();

                matching.add(
                    route
                );

                maxPriority =
                    priority;

                continue;
            }

            if (
                priority == maxPriority
            ) {
                matching.add(
                    route
                );
            }
        }

        if (matching.isEmpty()) {
            throw new IllegalStateException(
                "No preparation route matches kitchen routing unit."
            );
        }

        UUID winningKitchenId =
            matching
                .get(0)
                .getKitchenLocation()
                .getId();

        for (
            PreparationRoute candidate
            : matching
        ) {
            UUID candidateKitchenId =
                candidate
                    .getKitchenLocation()
                    .getId();

            if (
                !winningKitchenId.equals(
                    candidateKitchenId
                )
            ) {
                throw new IllegalStateException(
                    "Ambiguous highest-priority preparation routes target different kitchens."
                );
            }
        }

        PreparationRoute winner =
            matching
                .stream()
                .min(
                    Comparator.comparing(
                        PreparationRoute::getId
                    )
                )
                .orElseThrow();

        return new KitchenRouteResolution(
            unit,
            winner
        );
    }
}
