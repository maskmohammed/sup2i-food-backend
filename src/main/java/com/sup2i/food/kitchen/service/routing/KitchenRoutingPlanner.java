package com.sup2i.food.kitchen.service.routing;

import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.kitchen.domain.PreparationRoute;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderItem;
import com.sup2i.food.order.domain.OrderItemMenuSelection;
import com.sup2i.food.organization.domain.Location;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Component
public class KitchenRoutingPlanner {

    private final PreparationRouteResolver routeResolver;

    public KitchenRoutingPlanner(
        PreparationRouteResolver routeResolver
    ) {
        this.routeResolver =
            routeResolver;
    }

    public KitchenRoutingPlan plan(
        Order order,
        List<OrderItem> orderItems,
        List<OrderItemMenuSelection> menuSelections,
        List<PreparationRoute> effectiveRoutes
    ) {
        if (order == null) {
            throw new IllegalArgumentException(
                "Order is required for kitchen routing."
            );
        }

        if (
            orderItems == null
            || orderItems.isEmpty()
        ) {
            throw new IllegalStateException(
                "Order has no item to route."
            );
        }

        if (menuSelections == null) {
            throw new IllegalArgumentException(
                "Menu selection collection is required."
            );
        }

        if (
            effectiveRoutes == null
            || effectiveRoutes.isEmpty()
        ) {
            throw new IllegalStateException(
                "No effective preparation route exists for order location."
            );
        }

        UUID organizationId =
            order
                .getOrganization()
                .getId();

        UUID sourceLocationId =
            order
                .getLocation()
                .getId();

        validateRoutes(
            effectiveRoutes,
            organizationId,
            sourceLocationId
        );

        Set<UUID> orderItemIds =
            new HashSet<>();

        for (
            OrderItem orderItem
            : orderItems
        ) {
            if (orderItem == null) {
                throw new IllegalStateException(
                    "Order contains a null order item."
                );
            }

            UUID orderItemId =
                orderItem.getId();

            if (orderItemId == null) {
                throw new IllegalStateException(
                    "Order item must be persisted before kitchen routing."
                );
            }

            boolean unique =
                orderItemIds.add(
                    orderItemId
                );

            if (!unique) {
                throw new IllegalStateException(
                    "Order contains duplicate order item identifiers."
                );
            }

            if (
                orderItem.getOrder() == null
                || !order
                    .getId()
                    .equals(
                        orderItem
                            .getOrder()
                            .getId()
                    )
            ) {
                throw new IllegalStateException(
                    "Order item does not belong to routed order."
                );
            }
        }

        Map<
            UUID,
            List<OrderItemMenuSelection>
        > selectionsByOrderItem =
            indexSelections(
                menuSelections,
                orderItemIds,
                order.getId()
            );

        Map<UUID, TicketBucket> buckets =
            new TreeMap<>();

        Set<UnitKey> routedUnits =
            new HashSet<>();

        for (
            OrderItem orderItem
            : orderItems
        ) {
            List<OrderItemMenuSelection>
                selections =
                    selectionsByOrderItem
                        .getOrDefault(
                            orderItem.getId(),
                            List.of()
                        );

            if (selections.isEmpty()) {
                KitchenRoutingUnit unit =
                    rootUnit(
                        orderItem,
                        organizationId
                    );

                routeUnit(
                    unit,
                    effectiveRoutes,
                    organizationId,
                    sourceLocationId,
                    routedUnits,
                    buckets
                );

                continue;
            }

            for (
                OrderItemMenuSelection selection
                : selections
            ) {
                KitchenRoutingUnit unit =
                    selectionUnit(
                        orderItem,
                        selection,
                        organizationId
                    );

                routeUnit(
                    unit,
                    effectiveRoutes,
                    organizationId,
                    sourceLocationId,
                    routedUnits,
                    buckets
                );
            }
        }

        if (buckets.isEmpty()) {
            throw new IllegalStateException(
                "Kitchen routing produced no ticket."
            );
        }

        List<KitchenTicketPlan> ticketPlans =
            new ArrayList<>();

        for (
            TicketBucket bucket
            : buckets.values()
        ) {
            ticketPlans.add(
                bucket.toPlan()
            );
        }

        return new KitchenRoutingPlan(
            ticketPlans
        );
    }

    private Map<
        UUID,
        List<OrderItemMenuSelection>
    > indexSelections(
        List<OrderItemMenuSelection> menuSelections,
        Set<UUID> orderItemIds,
        UUID orderId
    ) {
        Map<
            UUID,
            List<OrderItemMenuSelection>
        > indexed =
            new HashMap<>();

        Set<UUID> selectionIds =
            new HashSet<>();

        for (
            OrderItemMenuSelection selection
            : menuSelections
        ) {
            if (selection == null) {
                throw new IllegalStateException(
                    "Order menu selection cannot be null."
                );
            }

            if (selection.getId() == null) {
                throw new IllegalStateException(
                    "Order menu selection must be persisted before kitchen routing."
                );
            }

            boolean unique =
                selectionIds.add(
                    selection.getId()
                );

            if (!unique) {
                throw new IllegalStateException(
                    "Duplicate order menu selection identifier."
                );
            }

            OrderItem parent =
                selection.getOrderItem();

            if (
                parent == null
                || parent.getId() == null
            ) {
                throw new IllegalStateException(
                    "Order menu selection has no persisted parent order item."
                );
            }

            if (
                !orderItemIds.contains(
                    parent.getId()
                )
            ) {
                throw new IllegalStateException(
                    "Order menu selection references an item outside routed order."
                );
            }

            if (
                parent.getOrder() == null
                || !orderId.equals(
                    parent
                        .getOrder()
                        .getId()
                )
            ) {
                throw new IllegalStateException(
                    "Order menu selection crosses order boundary."
                );
            }

            indexed
                .computeIfAbsent(
                    parent.getId(),
                    ignored ->
                        new ArrayList<>()
                )
                .add(
                    selection
                );
        }

        for (
            List<OrderItemMenuSelection> selections
            : indexed.values()
        ) {
            selections.sort(
                (left, right) ->
                    left
                        .getId()
                        .compareTo(
                            right.getId()
                        )
            );
        }

        return indexed;
    }

    private KitchenRoutingUnit rootUnit(
        OrderItem orderItem,
        UUID organizationId
    ) {
        Product product =
            orderItem.getProduct();

        ProductVariant variant =
            orderItem.getVariant();

        validateProduct(
            product,
            variant,
            organizationId
        );

        BigDecimal quantity =
            BigDecimal.valueOf(
                orderItem.getQuantity()
            );

        return new KitchenRoutingUnit(
            orderItem,
            null,
            product,
            variant,
            quantity
        );
    }

    private KitchenRoutingUnit selectionUnit(
        OrderItem orderItem,
        OrderItemMenuSelection selection,
        UUID organizationId
    ) {
        Product product =
            selection.getProduct();

        ProductVariant variant =
            selection.getVariant();

        validateProduct(
            product,
            variant,
            organizationId
        );

        BigDecimal selectionQuantity =
            selection.getQuantity();

        if (
            selectionQuantity == null
            || selectionQuantity.signum() <= 0
        ) {
            throw new IllegalStateException(
                "Order menu selection quantity must be positive."
            );
        }

        BigDecimal totalQuantity =
            selectionQuantity.multiply(
                BigDecimal.valueOf(
                    orderItem.getQuantity()
                )
            );

        return new KitchenRoutingUnit(
            orderItem,
            selection.getId(),
            product,
            variant,
            totalQuantity
        );
    }

    private void validateProduct(
        Product product,
        ProductVariant variant,
        UUID organizationId
    ) {
        if (product == null) {
            throw new IllegalStateException(
                "Kitchen routing product is missing."
            );
        }

        if (
            product.getOrganization() == null
            || !organizationId.equals(
                product
                    .getOrganization()
                    .getId()
            )
        ) {
            throw new IllegalStateException(
                "Kitchen routing product crosses organization boundary."
            );
        }

        if (variant == null) {
            return;
        }

        if (
            variant.getProduct() == null
            || !product
                .getId()
                .equals(
                    variant
                        .getProduct()
                        .getId()
                )
        ) {
            throw new IllegalStateException(
                "Kitchen routing variant does not belong to selected product."
            );
        }
    }

    private void routeUnit(
        KitchenRoutingUnit unit,
        List<PreparationRoute> effectiveRoutes,
        UUID organizationId,
        UUID sourceLocationId,
        Set<UnitKey> routedUnits,
        Map<UUID, TicketBucket> buckets
    ) {
        UnitKey key =
            new UnitKey(
                unit.orderItemId(),
                unit.menuSelectionId()
            );

        boolean unique =
            routedUnits.add(
                key
            );

        if (!unique) {
            throw new IllegalStateException(
                "Kitchen routing unit is duplicated."
            );
        }

        KitchenRouteResolution resolution =
            routeResolver.resolve(
                unit,
                effectiveRoutes
            );

        PreparationRoute route =
            resolution.route();

        validateRoute(
            route,
            organizationId,
            sourceLocationId
        );

        UUID kitchenLocationId =
            route
                .getKitchenLocation()
                .getId();

        TicketBucket bucket =
            buckets.get(
                kitchenLocationId
            );

        if (bucket == null) {
            bucket =
                new TicketBucket(
                    route.getKitchenLocation()
                );

            buckets.put(
                kitchenLocationId,
                bucket
            );
        }

        bucket.add(
            resolution
        );
    }

    private void validateRoutes(
        List<PreparationRoute> routes,
        UUID organizationId,
        UUID sourceLocationId
    ) {
        for (
            PreparationRoute route
            : routes
        ) {
            validateRoute(
                route,
                organizationId,
                sourceLocationId
            );
        }
    }

    private void validateRoute(
        PreparationRoute route,
        UUID organizationId,
        UUID sourceLocationId
    ) {
        if (route == null) {
            throw new IllegalStateException(
                "Preparation route cannot be null."
            );
        }

        Location sourceLocation =
            route.getSourceLocation();

        Location kitchenLocation =
            route.getKitchenLocation();

        if (
            sourceLocation == null
            || kitchenLocation == null
        ) {
            throw new IllegalStateException(
                "Preparation route locations are required."
            );
        }

        if (
            !sourceLocationId.equals(
                sourceLocation.getId()
            )
        ) {
            throw new IllegalStateException(
                "Preparation route source differs from order source location."
            );
        }

        if (
            sourceLocation
                .getId()
                .equals(
                    kitchenLocation.getId()
                )
        ) {
            throw new IllegalStateException(
                "Preparation source and kitchen location must differ."
            );
        }

        UUID sourceOrganizationId =
            sourceLocation
                .getCampus()
                .getOrganization()
                .getId();

        UUID kitchenOrganizationId =
            kitchenLocation
                .getCampus()
                .getOrganization()
                .getId();

        if (
            !organizationId.equals(
                sourceOrganizationId
            )
        ) {
            throw new IllegalStateException(
                "Preparation route source crosses organization boundary."
            );
        }

        if (
            !organizationId.equals(
                kitchenOrganizationId
            )
        ) {
            throw new IllegalStateException(
                "Preparation route kitchen crosses organization boundary."
            );
        }
    }

    private record UnitKey(
        UUID orderItemId,
        UUID menuSelectionId
    ) {
    }

    private static final class TicketBucket {

        private final Location kitchenLocation;

        private final List<KitchenRouteResolution>
            resolutions =
                new ArrayList<>();

        private int priority;

        private TicketBucket(
            Location kitchenLocation
        ) {
            this.kitchenLocation =
                kitchenLocation;
        }

        private void add(
            KitchenRouteResolution resolution
        ) {
            resolutions.add(
                resolution
            );

            priority =
                Math.max(
                    priority,
                    resolution
                        .route()
                        .getPriority()
                );
        }

        private KitchenTicketPlan toPlan() {

            PreparationRoute commonRoute =
                commonRoute();

            return new KitchenTicketPlan(
                kitchenLocation,
                commonRoute,
                priority,
                resolutions
            );
        }

        private PreparationRoute commonRoute() {

            PreparationRoute first =
                resolutions
                    .get(0)
                    .route();

            UUID firstRouteId =
                first.getId();

            for (
                KitchenRouteResolution resolution
                : resolutions
            ) {
                UUID candidateId =
                    resolution
                        .route()
                        .getId();

                if (
                    !Objects.equals(
                        firstRouteId,
                        candidateId
                    )
                ) {
                    return null;
                }
            }

            return first;
        }
    }
}
