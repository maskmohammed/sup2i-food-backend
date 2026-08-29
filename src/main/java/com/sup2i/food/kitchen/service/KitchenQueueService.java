package com.sup2i.food.kitchen.service;

import com.sup2i.food.kitchen.domain.KitchenTicket;
import com.sup2i.food.kitchen.domain.KitchenTicketItem;
import com.sup2i.food.kitchen.exception.KitchenConflictException;
import com.sup2i.food.kitchen.exception.KitchenErrorCode;
import com.sup2i.food.kitchen.repository.KitchenTicketItemRepository;
import com.sup2i.food.kitchen.repository.KitchenTicketRepository;
import com.sup2i.food.kitchen.repository.PreparationRouteRepository;
import com.sup2i.food.kitchen.service.routing.KitchenRouteResolution;
import com.sup2i.food.kitchen.service.routing.KitchenRoutingPlan;
import com.sup2i.food.kitchen.service.routing.KitchenRoutingPlanner;
import com.sup2i.food.kitchen.service.routing.KitchenTicketPlan;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderItem;
import com.sup2i.food.order.domain.OrderItemMenuSelection;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.domain.OrderStatusHistory;
import com.sup2i.food.order.domain.OrderStatusHistorySource;
import com.sup2i.food.order.repository.OrderItemMenuSelectionRepository;
import com.sup2i.food.order.repository.OrderItemRepository;
import com.sup2i.food.order.repository.OrderRepository;
import com.sup2i.food.order.repository.OrderStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class KitchenQueueService {

    private final OrderRepository orderRepository;

    private final OrderItemRepository orderItemRepository;

    private final OrderItemMenuSelectionRepository
        menuSelectionRepository;

    private final OrderStatusHistoryRepository
        historyRepository;

    private final PreparationRouteRepository
        routeRepository;

    private final KitchenTicketRepository
        ticketRepository;

    private final KitchenTicketItemRepository
        ticketItemRepository;

    private final KitchenRoutingPlanner
        routingPlanner;

    public KitchenQueueService(
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        OrderItemMenuSelectionRepository menuSelectionRepository,
        OrderStatusHistoryRepository historyRepository,
        PreparationRouteRepository routeRepository,
        KitchenTicketRepository ticketRepository,
        KitchenTicketItemRepository ticketItemRepository,
        KitchenRoutingPlanner routingPlanner
    ) {
        this.orderRepository =
            orderRepository;

        this.orderItemRepository =
            orderItemRepository;

        this.menuSelectionRepository =
            menuSelectionRepository;

        this.historyRepository =
            historyRepository;

        this.routeRepository =
            routeRepository;

        this.ticketRepository =
            ticketRepository;

        this.ticketItemRepository =
            ticketItemRepository;

        this.routingPlanner =
            routingPlanner;
    }

    @Transactional
    public List<KitchenTicket> queuePaidOrder(
        UUID organizationId,
        UUID orderId,
        OffsetDateTime at
    ) {
        requireIdentifier(
            organizationId,
            "Organization identifier is required."
        );

        requireIdentifier(
            orderId,
            "Order identifier is required."
        );

        if (at == null) {
            throw new IllegalArgumentException(
                "Kitchen queue timestamp is required."
            );
        }

        Order order =
            orderRepository
                .findOwnedByIdForUpdate(
                    orderId,
                    organizationId
                )
                .orElseThrow(() ->
                    new IllegalStateException(
                        "Paid order does not exist in organization."
                    )
                );

        List<KitchenTicket> existingTickets =
            ticketRepository
                .findAllByOrderForUpdate(
                    orderId,
                    organizationId
                );

        if (
            order.getStatus()
                == OrderStatus.QUEUED
        ) {
            if (existingTickets.isEmpty()) {
                throw new IllegalStateException(
                    "Queued order has no kitchen ticket."
                );
            }

            return List.copyOf(
                existingTickets
            );
        }

        if (
            order.getStatus()
                != OrderStatus.PAID
        ) {
            throw new KitchenConflictException(
                KitchenErrorCode.INVALID_ORDER_STATUS,
                "Only a paid order can enter the kitchen queue."
            );
        }

        if (!existingTickets.isEmpty()) {
            throw new KitchenConflictException(
                KitchenErrorCode.CONCURRENT_MODIFICATION,
                "Paid order already owns kitchen tickets."
            );
        }

        List<OrderItem> orderItems =
            orderItemRepository
                .findAllByOrder_IdOrderByIdAsc(
                    orderId
                );

        if (orderItems.isEmpty()) {
            throw new IllegalStateException(
                "Paid order has no item to route."
            );
        }

        List<OrderItemMenuSelection>
            menuSelections =
                menuSelectionRepository
                    .findAllByOrderId(
                        orderId
                    );

        List<com.sup2i.food.kitchen.domain.PreparationRoute>
            effectiveRoutes =
                routeRepository
                    .findEffectiveForSource(
                        order
                            .getLocation()
                            .getId(),
                        at
                    );

        KitchenRoutingPlan plan =
            routingPlanner.plan(
                order,
                orderItems,
                menuSelections,
                effectiveRoutes
            );

        List<KitchenTicket> createdTickets =
            materialize(
                order,
                plan
            );

        /*
         * Flush the complete ticket structure before changing
         * the global Order state. Any V055 structural violation
         * therefore aborts while the Order is still PAID.
         */
        ticketRepository.flush();
        ticketItemRepository.flush();

        OrderStatus fromStatus =
            order.getStatus();

        order.markQueued();

        orderRepository.save(
            order
        );

        historyRepository.save(
            new OrderStatusHistory(
                order,
                fromStatus,
                OrderStatus.QUEUED,
                null,
                "Order queued for kitchen.",
                OrderStatusHistorySource.SYSTEM
            )
        );

        orderRepository.flush();
        historyRepository.flush();

        return List.copyOf(
            createdTickets
        );
    }

    private List<KitchenTicket> materialize(
        Order order,
        KitchenRoutingPlan plan
    ) {
        List<KitchenTicket> tickets =
            new ArrayList<>();

        for (
            KitchenTicketPlan ticketPlan
            : plan.tickets()
        ) {
            KitchenTicket ticket =
                new KitchenTicket(
                    order,
                    ticketPlan.kitchenLocation(),
                    ticketPlan.preparationRoute(),
                    ticketPlan.priority()
                );

            ticket =
                ticketRepository.save(
                    ticket
                );

            for (
                KitchenRouteResolution resolution
                : ticketPlan.resolutions()
            ) {
                ticketItemRepository.save(
                    new KitchenTicketItem(
                        ticket,
                        resolution
                            .unit()
                            .orderItem(),
                        resolution
                            .unit()
                            .menuSelectionId(),
                        resolution
                            .unit()
                            .quantity()
                    )
                );
            }

            tickets.add(
                ticket
            );
        }

        if (tickets.isEmpty()) {
            throw new IllegalStateException(
                "Kitchen routing plan materialized no ticket."
            );
        }

        return tickets;
    }

    private void requireIdentifier(
        UUID value,
        String message
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                message
            );
        }
    }
}
