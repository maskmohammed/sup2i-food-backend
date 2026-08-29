package com.sup2i.food.kitchen;

import com.sup2i.food.kitchen.domain.KitchenTicket;
import com.sup2i.food.kitchen.domain.KitchenTicketItem;
import com.sup2i.food.kitchen.domain.PreparationRoute;
import com.sup2i.food.kitchen.exception.KitchenConflictException;
import com.sup2i.food.kitchen.exception.KitchenErrorCode;
import com.sup2i.food.kitchen.repository.KitchenTicketItemRepository;
import com.sup2i.food.kitchen.repository.KitchenTicketRepository;
import com.sup2i.food.kitchen.repository.PreparationRouteRepository;
import com.sup2i.food.kitchen.service.KitchenQueueService;
import com.sup2i.food.kitchen.service.routing.KitchenRouteResolution;
import com.sup2i.food.kitchen.service.routing.KitchenRoutingPlan;
import com.sup2i.food.kitchen.service.routing.KitchenRoutingPlanner;
import com.sup2i.food.kitchen.service.routing.KitchenRoutingUnit;
import com.sup2i.food.kitchen.service.routing.KitchenTicketPlan;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderItem;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.domain.OrderStatusHistory;
import com.sup2i.food.order.domain.OrderStatusHistorySource;
import com.sup2i.food.order.repository.OrderItemMenuSelectionRepository;
import com.sup2i.food.order.repository.OrderItemRepository;
import com.sup2i.food.order.repository.OrderRepository;
import com.sup2i.food.order.repository.OrderStatusHistoryRepository;
import com.sup2i.food.organization.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitchenQueueServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderItemMenuSelectionRepository
        menuSelectionRepository;

    @Mock
    private OrderStatusHistoryRepository
        historyRepository;

    @Mock
    private PreparationRouteRepository
        routeRepository;

    @Mock
    private KitchenTicketRepository
        ticketRepository;

    @Mock
    private KitchenTicketItemRepository
        ticketItemRepository;

    @Mock
    private KitchenRoutingPlanner routingPlanner;

    private KitchenQueueService service;

    @BeforeEach
    void setUp() {
        service =
            new KitchenQueueService(
                orderRepository,
                orderItemRepository,
                menuSelectionRepository,
                historyRepository,
                routeRepository,
                ticketRepository,
                ticketItemRepository,
                routingPlanner
            );
    }

    @Test
    void paidOrderMaterializesPlanThenMovesToQueued() {

        UUID organizationId =
            UUID.randomUUID();

        UUID orderId =
            UUID.randomUUID();

        UUID sourceLocationId =
            UUID.randomUUID();

        OffsetDateTime at =
            OffsetDateTime.parse(
                "2026-08-28T10:00:00+01:00"
            );

        Order order =
            org.mockito.Mockito.mock(
                Order.class
            );

        OrderItem orderItem =
            org.mockito.Mockito.mock(
                OrderItem.class
            );

        Location sourceLocation =
            org.mockito.Mockito.mock(
                Location.class
            );

        Location kitchenLocation =
            org.mockito.Mockito.mock(
                Location.class
            );

        PreparationRoute route =
            org.mockito.Mockito.mock(
                PreparationRoute.class
            );

        when(order.getStatus())
            .thenReturn(
                OrderStatus.PAID
            );

        when(order.getLocation())
            .thenReturn(
                sourceLocation
            );

        when(sourceLocation.getId())
            .thenReturn(
                sourceLocationId
            );

        when(
            orderRepository
                .findOwnedByIdForUpdate(
                    orderId,
                    organizationId
                )
        ).thenReturn(
            Optional.of(
                order
            )
        );

        when(
            ticketRepository
                .findAllByOrderForUpdate(
                    orderId,
                    organizationId
                )
        ).thenReturn(
            List.of()
        );

        when(
            orderItemRepository
                .findAllByOrder_IdOrderByIdAsc(
                    orderId
                )
        ).thenReturn(
            List.of(
                orderItem
            )
        );

        when(
            menuSelectionRepository
                .findAllByOrderId(
                    orderId
                )
        ).thenReturn(
            List.of()
        );

        when(
            routeRepository
                .findEffectiveForSource(
                    sourceLocationId,
                    at
                )
        ).thenReturn(
            List.of(
                route
            )
        );

        KitchenRoutingUnit unit =
            new KitchenRoutingUnit(
                orderItem,
                null,
                org.mockito.Mockito.mock(
                    com.sup2i.food.catalog.domain.Product.class
                ),
                null,
                BigDecimal.valueOf(2)
            );

        KitchenRouteResolution resolution =
            new KitchenRouteResolution(
                unit,
                route
            );

        KitchenTicketPlan ticketPlan =
            new KitchenTicketPlan(
                kitchenLocation,
                route,
                7,
                List.of(
                    resolution
                )
            );

        KitchenRoutingPlan plan =
            new KitchenRoutingPlan(
                List.of(
                    ticketPlan
                )
            );

        when(
            routingPlanner.plan(
                order,
                List.of(
                    orderItem
                ),
                List.of(),
                List.of(
                    route
                )
            )
        ).thenReturn(
            plan
        );

        when(
            ticketRepository.save(
                any(
                    KitchenTicket.class
                )
            )
        ).thenAnswer(
            invocation ->
                invocation.getArgument(0)
        );

        List<KitchenTicket> result =
            service.queuePaidOrder(
                organizationId,
                orderId,
                at
            );

        assertThat(result)
            .hasSize(1);

        ArgumentCaptor<KitchenTicket>
            ticketCaptor =
                ArgumentCaptor.forClass(
                    KitchenTicket.class
                );

        verify(ticketRepository)
            .save(
                ticketCaptor.capture()
            );

        KitchenTicket savedTicket =
            ticketCaptor.getValue();

        assertThat(
            savedTicket.getOrder()
        ).isSameAs(
            order
        );

        assertThat(
            savedTicket.getKitchenLocation()
        ).isSameAs(
            kitchenLocation
        );

        assertThat(
            savedTicket.getPreparationRoute()
        ).isSameAs(
            route
        );

        assertThat(
            savedTicket.getPriority()
        ).isEqualTo(
            7
        );

        ArgumentCaptor<KitchenTicketItem>
            itemCaptor =
                ArgumentCaptor.forClass(
                    KitchenTicketItem.class
                );

        verify(ticketItemRepository)
            .save(
                itemCaptor.capture()
            );

        KitchenTicketItem savedItem =
            itemCaptor.getValue();

        assertThat(
            savedItem.getKitchenTicket()
        ).isSameAs(
            savedTicket
        );

        assertThat(
            savedItem.getOrderItem()
        ).isSameAs(
            orderItem
        );

        assertThat(
            savedItem.getMenuSelectionId()
        ).isNull();

        assertThat(
            savedItem.getQuantity()
        ).isEqualByComparingTo(
            "2"
        );

        verify(ticketRepository)
            .flush();

        verify(ticketItemRepository)
            .flush();

        verify(order)
            .markQueued();

        verify(orderRepository)
            .save(
                order
            );

        ArgumentCaptor<OrderStatusHistory>
            historyCaptor =
                ArgumentCaptor.forClass(
                    OrderStatusHistory.class
                );

        verify(historyRepository)
            .save(
                historyCaptor.capture()
            );

        OrderStatusHistory history =
            historyCaptor.getValue();

        assertThat(
            history.getFromStatus()
        ).isEqualTo(
            OrderStatus.PAID
        );

        assertThat(
            history.getToStatus()
        ).isEqualTo(
            OrderStatus.QUEUED
        );

        assertThat(
            history.getSource()
        ).isEqualTo(
            OrderStatusHistorySource.SYSTEM
        );

        assertThat(
            history.getChangedBy()
        ).isNull();
    }

    @Test
    void queuedOrderReplaysExistingTicketsWithoutPlanningAgain() {

        UUID organizationId =
            UUID.randomUUID();

        UUID orderId =
            UUID.randomUUID();

        Order order =
            org.mockito.Mockito.mock(
                Order.class
            );

        KitchenTicket existing =
            org.mockito.Mockito.mock(
                KitchenTicket.class
            );

        when(order.getStatus())
            .thenReturn(
                OrderStatus.QUEUED
            );

        when(
            orderRepository
                .findOwnedByIdForUpdate(
                    orderId,
                    organizationId
                )
        ).thenReturn(
            Optional.of(
                order
            )
        );

        when(
            ticketRepository
                .findAllByOrderForUpdate(
                    orderId,
                    organizationId
                )
        ).thenReturn(
            List.of(
                existing
            )
        );

        List<KitchenTicket> result =
            service.queuePaidOrder(
                organizationId,
                orderId,
                OffsetDateTime.now()
            );

        assertThat(result)
            .containsExactly(
                existing
            );

        verify(
            routingPlanner,
            never()
        ).plan(
            any(),
            any(),
            any(),
            any()
        );

        verify(
            orderItemRepository,
            never()
        ).findAllByOrder_IdOrderByIdAsc(
            any()
        );

        verify(
            order,
            never()
        ).markQueued();
    }

    @Test
    void queuedOrderWithoutTicketFailsAsInvariantViolation() {

        UUID organizationId =
            UUID.randomUUID();

        UUID orderId =
            UUID.randomUUID();

        Order order =
            org.mockito.Mockito.mock(
                Order.class
            );

        when(order.getStatus())
            .thenReturn(
                OrderStatus.QUEUED
            );

        when(
            orderRepository
                .findOwnedByIdForUpdate(
                    orderId,
                    organizationId
                )
        ).thenReturn(
            Optional.of(
                order
            )
        );

        when(
            ticketRepository
                .findAllByOrderForUpdate(
                    orderId,
                    organizationId
                )
        ).thenReturn(
            List.of()
        );

        assertThatThrownBy(() ->
            service.queuePaidOrder(
                organizationId,
                orderId,
                OffsetDateTime.now()
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessageContaining(
                "Queued order has no kitchen ticket"
            );

        verify(
            routingPlanner,
            never()
        ).plan(
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void nonPaidOrderIsRejectedBeforeRouting() {

        UUID organizationId =
            UUID.randomUUID();

        UUID orderId =
            UUID.randomUUID();

        Order order =
            org.mockito.Mockito.mock(
                Order.class
            );

        when(order.getStatus())
            .thenReturn(
                OrderStatus.PREPARING
            );

        when(
            orderRepository
                .findOwnedByIdForUpdate(
                    orderId,
                    organizationId
                )
        ).thenReturn(
            Optional.of(
                order
            )
        );

        when(
            ticketRepository
                .findAllByOrderForUpdate(
                    orderId,
                    organizationId
                )
        ).thenReturn(
            List.of()
        );

        assertThatThrownBy(() ->
            service.queuePaidOrder(
                organizationId,
                orderId,
                OffsetDateTime.now()
            )
        )
            .isInstanceOfSatisfying(
                KitchenConflictException.class,
                exception ->
                    assertThat(
                        exception.getErrorCode()
                    ).isEqualTo(
                        KitchenErrorCode
                            .INVALID_ORDER_STATUS
                    )
            );

        verify(
            routingPlanner,
            never()
        ).plan(
            any(),
            any(),
            any(),
            any()
        );
    }
}
