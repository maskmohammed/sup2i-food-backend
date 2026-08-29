package com.sup2i.food.kitchen;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.kitchen.domain.KitchenTicket;
import com.sup2i.food.kitchen.domain.KitchenTicketItem;
import com.sup2i.food.kitchen.domain.KitchenTicketItemStatus;
import com.sup2i.food.kitchen.domain.KitchenTicketStatus;
import com.sup2i.food.kitchen.exception.KitchenConflictException;
import com.sup2i.food.kitchen.exception.KitchenNotFoundException;
import com.sup2i.food.kitchen.repository.KitchenTicketItemRepository;
import com.sup2i.food.kitchen.repository.KitchenTicketRepository;
import com.sup2i.food.kitchen.service.KitchenStartService;
import com.sup2i.food.kitchen.service.PreparedStockConsumptionService;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.domain.OrderStatusHistory;
import com.sup2i.food.order.domain.OrderStatusHistorySource;
import com.sup2i.food.order.repository.OrderRepository;
import com.sup2i.food.order.repository.OrderStatusHistoryRepository;
import com.sup2i.food.organization.domain.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitchenStartServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KitchenTicketRepository ticketRepository;

    @Mock
    private KitchenTicketItemRepository ticketItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @Mock
    private PreparedStockConsumptionService stockConsumptionService;

    private KitchenStartService service;

    @BeforeEach
    void setUp() {
        service =
            new KitchenStartService(
                userRepository,
                ticketRepository,
                ticketItemRepository,
                orderRepository,
                historyRepository,
                stockConsumptionService
            );
    }

    @Test
    void firstQueuedTicketConsumesStockAndMovesOrderToPreparing() {
        StartFixture fixture =
            fixture(
                OrderStatus.QUEUED,
                KitchenTicketStatus.QUEUED,
                KitchenTicketItemStatus.QUEUED
            );

        when(
            stockConsumptionService
                .consumePreparedReservations(
                    fixture.order(),
                    fixture.actor(),
                    fixture.at()
                )
        ).thenReturn(true);

        KitchenTicket result =
            service.startTicket(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            );

        assertThat(result)
            .isSameAs(
                fixture.target()
            );

        InOrder locks =
            inOrder(
                ticketRepository,
                orderRepository,
                ticketItemRepository
            );

        locks.verify(ticketRepository)
            .findOwnedById(
                fixture.ticketId(),
                fixture.organizationId()
            );

        locks.verify(orderRepository)
            .findOwnedByIdForUpdate(
                fixture.orderId(),
                fixture.organizationId()
            );

        locks.verify(ticketRepository)
            .findAllByOrderForUpdate(
                fixture.orderId(),
                fixture.organizationId()
            );

        locks.verify(ticketItemRepository)
            .findAllByTicketForUpdate(
                fixture.ticketId()
            );

        verify(stockConsumptionService)
            .consumePreparedReservations(
                fixture.order(),
                fixture.actor(),
                fixture.at()
            );

        verify(fixture.target())
            .markPreparing(
                fixture.actor(),
                fixture.at()
            );

        verify(fixture.item())
            .markPreparing(
                fixture.at()
            );

        verify(fixture.order())
            .markPreparing();

        ArgumentCaptor<OrderStatusHistory> historyCaptor =
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
            OrderStatus.QUEUED
        );

        assertThat(
            history.getToStatus()
        ).isEqualTo(
            OrderStatus.PREPARING
        );

        assertThat(
            history.getChangedBy()
        ).isSameAs(
            fixture.actor()
        );

        assertThat(
            history.getReason()
        ).isEqualTo(
            "Kitchen preparation started."
        );

        assertThat(
            history.getSource()
        ).isEqualTo(
            OrderStatusHistorySource.API
        );

        verify(ticketItemRepository)
            .saveAllAndFlush(
                List.of(
                    fixture.item()
                )
            );

        verify(ticketRepository)
            .saveAndFlush(
                fixture.target()
            );

        verify(orderRepository)
            .saveAndFlush(
                fixture.order()
            );
    }

    @Test
    void acceptedSiblingStartsWithoutSecondStockConsumption() {
        StartFixture fixture =
            fixture(
                OrderStatus.PREPARING,
                KitchenTicketStatus.ACCEPTED,
                KitchenTicketItemStatus.QUEUED
            );

        KitchenTicket sibling =
            org.mockito.Mockito.mock(
                KitchenTicket.class
            );

        when(sibling.getId())
            .thenReturn(
                UUID.randomUUID()
            );

        when(sibling.getStatus())
            .thenReturn(
                KitchenTicketStatus.PREPARING
            );

        when(
            ticketRepository
                .findAllByOrderForUpdate(
                    fixture.orderId(),
                    fixture.organizationId()
                )
        ).thenReturn(
            List.of(
                fixture.target(),
                sibling
            )
        );

        KitchenTicket result =
            service.startTicket(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            );

        assertThat(result)
            .isSameAs(
                fixture.target()
            );

        verifyNoInteractions(
            stockConsumptionService
        );

        verify(fixture.target())
            .markPreparing(
                fixture.actor(),
                fixture.at()
            );

        verify(fixture.item())
            .markPreparing(
                fixture.at()
            );

        verify(fixture.order(), never())
            .markPreparing();

        verifyNoInteractions(
            historyRepository
        );

        verify(orderRepository, never())
            .saveAndFlush(
                fixture.order()
            );
    }

    @Test
    void preparingReplayIsPureNoOp() {
        StartFixture fixture =
            fixture(
                OrderStatus.PREPARING,
                KitchenTicketStatus.PREPARING,
                KitchenTicketItemStatus.PREPARING
            );

        KitchenTicket result =
            service.startTicket(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            );

        assertThat(result)
            .isSameAs(
                fixture.target()
            );

        verifyNoInteractions(
            stockConsumptionService
        );

        verify(fixture.target(), never())
            .markPreparing(
                fixture.actor(),
                fixture.at()
            );

        verify(fixture.item(), never())
            .markPreparing(
                fixture.at()
            );

        verifyNoInteractions(
            historyRepository
        );

        verify(ticketRepository, never())
            .saveAndFlush(
                fixture.target()
            );
    }

    @Test
    void malformedPreparingReplayIsRejected() {
        StartFixture fixture =
            fixture(
                OrderStatus.PREPARING,
                KitchenTicketStatus.PREPARING,
                KitchenTicketItemStatus.QUEUED
            );

        assertThatThrownBy(() ->
            service.startTicket(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            )
        )
            .isInstanceOf(
                KitchenConflictException.class
            )
            .hasMessageContaining(
                "inconsistent"
            );

        verifyNoInteractions(
            stockConsumptionService
        );
    }

    @Test
    void readyTicketCannotStart() {
        StartFixture fixture =
            fixture(
                OrderStatus.PREPARING,
                KitchenTicketStatus.READY,
                KitchenTicketItemStatus.QUEUED
            );

        assertThatThrownBy(() ->
            service.startTicket(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            )
        )
            .isInstanceOf(
                KitchenConflictException.class
            )
            .hasMessageContaining(
                "current status"
            );

        verifyNoInteractions(
            stockConsumptionService
        );
    }

    @Test
    void paidOrderCannotStartBeforeQueueTransition() {
        StartFixture fixture =
            fixture(
                OrderStatus.PAID,
                KitchenTicketStatus.QUEUED,
                KitchenTicketItemStatus.QUEUED
            );

        assertThatThrownBy(() ->
            service.startTicket(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            )
        )
            .isInstanceOf(
                KitchenConflictException.class
            )
            .hasMessageContaining(
                "Order cannot enter kitchen preparation"
            );

        verifyNoInteractions(
            stockConsumptionService
        );
    }

    @Test
    void unknownTenantTicketIsHiddenAsNotFound() {
        UUID actorId =
            UUID.randomUUID();

        UUID ticketId =
            UUID.randomUUID();

        UUID organizationId =
            UUID.randomUUID();

        User actor =
            org.mockito.Mockito.mock(
                User.class
            );

        Organization organization =
            org.mockito.Mockito.mock(
                Organization.class
            );

        when(
            organization.getId()
        ).thenReturn(
            organizationId
        );

        when(
            actor.getOrganization()
        ).thenReturn(
            organization
        );

        when(
            userRepository.findById(
                actorId
            )
        ).thenReturn(
            Optional.of(
                actor
            )
        );

        when(
            ticketRepository.findOwnedById(
                ticketId,
                organizationId
            )
        ).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() ->
            service.startTicket(
                actorId,
                ticketId,
                OffsetDateTime.parse(
                    "2026-08-29T10:00:00+01:00"
                )
            )
        )
            .isInstanceOf(
                KitchenNotFoundException.class
            );

        verifyNoInteractions(
            orderRepository,
            ticketItemRepository,
            stockConsumptionService,
            historyRepository
        );
    }

    @Test
    void emptyTicketCannotStart() {
        StartFixture fixture =
            fixture(
                OrderStatus.QUEUED,
                KitchenTicketStatus.QUEUED,
                KitchenTicketItemStatus.QUEUED
            );

        when(
            ticketItemRepository
                .findAllByTicketForUpdate(
                    fixture.ticketId()
                )
        ).thenReturn(
            List.of()
        );

        assertThatThrownBy(() ->
            service.startTicket(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            )
        )
            .isInstanceOf(
                KitchenConflictException.class
            )
            .hasMessageContaining(
                "without items"
            );

        verifyNoInteractions(
            stockConsumptionService
        );
    }

    private StartFixture fixture(
        OrderStatus orderStatus,
        KitchenTicketStatus ticketStatus,
        KitchenTicketItemStatus itemStatus
    ) {
        UUID actorId =
            UUID.randomUUID();

        UUID ticketId =
            UUID.randomUUID();

        UUID orderId =
            UUID.randomUUID();

        UUID organizationId =
            UUID.randomUUID();

        OffsetDateTime at =
            OffsetDateTime.parse(
                "2026-08-29T10:00:00+01:00"
            );

        Organization organization =
            org.mockito.Mockito.mock(
                Organization.class
            );

        User actor =
            org.mockito.Mockito.mock(
                User.class
            );

        Order discoveryOrder =
            org.mockito.Mockito.mock(
                Order.class
            );

        KitchenTicket discovered =
            org.mockito.Mockito.mock(
                KitchenTicket.class
            );

        Order order =
            org.mockito.Mockito.mock(
                Order.class
            );

        KitchenTicket target =
            org.mockito.Mockito.mock(
                KitchenTicket.class
            );

        KitchenTicketItem item =
            org.mockito.Mockito.mock(
                KitchenTicketItem.class
            );

        lenient().when(
            organization.getId()
        ).thenReturn(
            organizationId
        );

        lenient().when(
            actor.getOrganization()
        ).thenReturn(
            organization
        );

        lenient().when(
            userRepository.findById(
                actorId
            )
        ).thenReturn(
            Optional.of(
                actor
            )
        );

        lenient().when(
            discoveryOrder.getId()
        ).thenReturn(
            orderId
        );

        lenient().when(
            discovered.getOrder()
        ).thenReturn(
            discoveryOrder
        );

        lenient().when(
            ticketRepository.findOwnedById(
                ticketId,
                organizationId
            )
        ).thenReturn(
            Optional.of(
                discovered
            )
        );

        lenient().when(
            order.getId()
        ).thenReturn(
            orderId
        );

        lenient().when(
            order.getStatus()
        ).thenReturn(
            orderStatus
        );

        lenient().when(
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

        lenient().when(
            target.getId()
        ).thenReturn(
            ticketId
        );

        lenient().when(
            target.getStatus()
        ).thenReturn(
            ticketStatus
        );

        lenient().when(
            ticketRepository
                .findAllByOrderForUpdate(
                    orderId,
                    organizationId
                )
        ).thenReturn(
            List.of(
                target
            )
        );

        lenient().when(
            item.getStatus()
        ).thenReturn(
            itemStatus
        );

        lenient().when(
            ticketItemRepository
                .findAllByTicketForUpdate(
                    ticketId
                )
        ).thenReturn(
            List.of(
                item
            )
        );

        return new StartFixture(
            actorId,
            ticketId,
            orderId,
            organizationId,
            at,
            actor,
            order,
            target,
            item
        );
    }

    private record StartFixture(
        UUID actorId,
        UUID ticketId,
        UUID orderId,
        UUID organizationId,
        OffsetDateTime at,
        User actor,
        Order order,
        KitchenTicket target,
        KitchenTicketItem item
    ) {
    }
}
