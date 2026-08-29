package com.sup2i.food.kitchen;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.kitchen.domain.KitchenTicket;
import com.sup2i.food.kitchen.domain.KitchenTicketItem;
import com.sup2i.food.kitchen.domain.KitchenTicketItemStatus;
import com.sup2i.food.kitchen.domain.KitchenTicketStatus;
import com.sup2i.food.kitchen.exception.KitchenConflictException;
import com.sup2i.food.kitchen.exception.KitchenErrorCode;
import com.sup2i.food.kitchen.exception.KitchenNotFoundException;
import com.sup2i.food.kitchen.repository.KitchenTicketItemRepository;
import com.sup2i.food.kitchen.repository.KitchenTicketRepository;
import com.sup2i.food.kitchen.service.KitchenReadyService;
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
import java.util.ArrayList;
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
class KitchenReadyServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KitchenTicketRepository ticketRepository;

    @Mock
    private KitchenTicketItemRepository
        ticketItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository
        historyRepository;

    private KitchenReadyService service;

    @BeforeEach
    void setUp() {

        service =
            new KitchenReadyService(
                userRepository,
                ticketRepository,
                ticketItemRepository,
                orderRepository,
                historyRepository
            );
    }

    @Test
    void firstReadyKitchenLeavesOrderPreparing() {

        ReadyFixture fixture =
            fixture(
                OrderStatus.PREPARING,
                KitchenTicketStatus.PREPARING,
                KitchenTicketItemStatus.PREPARING,
                KitchenTicketStatus.PREPARING
            );

        KitchenTicket result =
            service.markReady(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            );

        assertThat(result)
            .isSameAs(
                fixture.target()
            );

        InOrder lockOrder =
            inOrder(
                ticketRepository,
                orderRepository,
                ticketItemRepository
            );

        lockOrder.verify(ticketRepository)
            .findOwnedById(
                fixture.ticketId(),
                fixture.organizationId()
            );

        lockOrder.verify(orderRepository)
            .findOwnedByIdForUpdate(
                fixture.orderId(),
                fixture.organizationId()
            );

        lockOrder.verify(ticketRepository)
            .findAllByOrderForUpdate(
                fixture.orderId(),
                fixture.organizationId()
            );

        lockOrder.verify(ticketItemRepository)
            .findAllByTicketForUpdate(
                fixture.ticketId()
            );

        verify(fixture.target())
            .markReady(
                fixture.at()
            );

        verify(fixture.item())
            .markReady(
                fixture.at()
            );

        verify(ticketItemRepository)
            .saveAllAndFlush(
                fixture.items()
            );

        verify(ticketRepository)
            .saveAndFlush(
                fixture.target()
            );

        verify(fixture.order(), never())
            .markReady(
                fixture.at()
            );

        verify(orderRepository, never())
            .saveAndFlush(
                fixture.order()
            );

        verifyNoInteractions(
            historyRepository
        );
    }

    @Test
    void lastReadyKitchenMovesOrderReadyAndWritesSingleHistory() {

        ReadyFixture fixture =
            fixture(
                OrderStatus.PREPARING,
                KitchenTicketStatus.PREPARING,
                KitchenTicketItemStatus.PREPARING,
                KitchenTicketStatus.READY
            );

        KitchenTicket result =
            service.markReady(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            );

        assertThat(result)
            .isSameAs(
                fixture.target()
            );

        verify(fixture.target())
            .markReady(
                fixture.at()
            );

        verify(fixture.item())
            .markReady(
                fixture.at()
            );

        verify(fixture.order())
            .markReady(
                fixture.at()
            );

        verify(orderRepository)
            .saveAndFlush(
                fixture.order()
            );

        ArgumentCaptor<OrderStatusHistory>
            historyCaptor =
                ArgumentCaptor.forClass(
                    OrderStatusHistory.class
                );

        verify(historyRepository)
            .saveAndFlush(
                historyCaptor.capture()
            );

        OrderStatusHistory history =
            historyCaptor.getValue();

        assertThat(
            history.getFromStatus()
        ).isEqualTo(
            OrderStatus.PREPARING
        );

        assertThat(
            history.getToStatus()
        ).isEqualTo(
            OrderStatus.READY
        );

        assertThat(
            history.getChangedBy()
        ).isSameAs(
            fixture.actor()
        );

        assertThat(
            history.getReason()
        ).isEqualTo(
            "Order ready for collection."
        );

        assertThat(
            history.getSource()
        ).isEqualTo(
            OrderStatusHistorySource.API
        );
    }

    @Test
    void cancelledSiblingBlocksReadyBeforeAnyMutation() {

        ReadyFixture fixture =
            fixture(
                OrderStatus.PREPARING,
                KitchenTicketStatus.PREPARING,
                KitchenTicketItemStatus.PREPARING,
                KitchenTicketStatus.CANCELLED
            );

        assertThatThrownBy(() ->
            service.markReady(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            )
        )
            .isInstanceOfSatisfying(
                KitchenConflictException.class,
                exception ->
                    assertThat(
                        exception.getErrorCode()
                    ).isEqualTo(
                        KitchenErrorCode.CONCURRENT_MODIFICATION
                    )
            )
            .hasMessageContaining(
                "cancelled sibling"
            );

        verify(fixture.target(), never())
            .markReady(
                fixture.at()
            );

        verify(fixture.item(), never())
            .markReady(
                fixture.at()
            );

        verify(ticketRepository, never())
            .saveAndFlush(
                fixture.target()
            );

        verifyNoInteractions(
            historyRepository
        );
    }

    @Test
    void readyReplayWhileSiblingStillPreparingIsPureNoOp() {

        ReadyFixture fixture =
            fixture(
                OrderStatus.PREPARING,
                KitchenTicketStatus.READY,
                KitchenTicketItemStatus.READY,
                KitchenTicketStatus.PREPARING
            );

        KitchenTicket result =
            service.markReady(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            );

        assertThat(result)
            .isSameAs(
                fixture.target()
            );

        assertReplayHasNoMutation(
            fixture
        );
    }

    @Test
    void finalReadyReplayOnReadyOrderIsPureNoOp() {

        ReadyFixture fixture =
            fixture(
                OrderStatus.READY,
                KitchenTicketStatus.READY,
                KitchenTicketItemStatus.READY,
                KitchenTicketStatus.READY
            );

        KitchenTicket result =
            service.markReady(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            );

        assertThat(result)
            .isSameAs(
                fixture.target()
            );

        assertReplayHasNoMutation(
            fixture
        );
    }

    @Test
    void replayDetectsMissingGlobalReadyTransition() {

        ReadyFixture fixture =
            fixture(
                OrderStatus.PREPARING,
                KitchenTicketStatus.READY,
                KitchenTicketItemStatus.READY,
                KitchenTicketStatus.READY
            );

        assertThatThrownBy(() ->
            service.markReady(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            )
        )
            .isInstanceOfSatisfying(
                KitchenConflictException.class,
                exception ->
                    assertThat(
                        exception.getErrorCode()
                    ).isEqualTo(
                        KitchenErrorCode.CONCURRENT_MODIFICATION
                    )
            )
            .hasMessageContaining(
                "order is still preparing"
            );

        assertReplayHasNoMutation(
            fixture
        );
    }

    @Test
    void replayDetectsReadyOrderWithIncompleteSibling() {

        ReadyFixture fixture =
            fixture(
                OrderStatus.READY,
                KitchenTicketStatus.READY,
                KitchenTicketItemStatus.READY,
                KitchenTicketStatus.PREPARING
            );

        assertThatThrownBy(() ->
            service.markReady(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            )
        )
            .isInstanceOfSatisfying(
                KitchenConflictException.class,
                exception ->
                    assertThat(
                        exception.getErrorCode()
                    ).isEqualTo(
                        KitchenErrorCode.CONCURRENT_MODIFICATION
                    )
            )
            .hasMessageContaining(
                "workflow is still incomplete"
            );

        assertReplayHasNoMutation(
            fixture
        );
    }

    @Test
    void queuedTicketCannotBecomeReady() {

        ReadyFixture fixture =
            fixture(
                OrderStatus.PREPARING,
                KitchenTicketStatus.QUEUED,
                KitchenTicketItemStatus.QUEUED,
                null
            );

        assertThatThrownBy(() ->
            service.markReady(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            )
        )
            .isInstanceOfSatisfying(
                KitchenConflictException.class,
                exception ->
                    assertThat(
                        exception.getErrorCode()
                    ).isEqualTo(
                        KitchenErrorCode.INVALID_KITCHEN_STATUS
                    )
            );

        verify(fixture.target(), never())
            .markReady(
                fixture.at()
            );
    }

    @Test
    void preparingTicketRequiresPreparingOrder() {

        ReadyFixture fixture =
            fixture(
                OrderStatus.QUEUED,
                KitchenTicketStatus.PREPARING,
                KitchenTicketItemStatus.PREPARING,
                null
            );

        assertThatThrownBy(() ->
            service.markReady(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            )
        )
            .isInstanceOfSatisfying(
                KitchenConflictException.class,
                exception ->
                    assertThat(
                        exception.getErrorCode()
                    ).isEqualTo(
                        KitchenErrorCode.INVALID_ORDER_STATUS
                    )
            );

        verify(fixture.target(), never())
            .markReady(
                fixture.at()
            );
    }

    @Test
    void preparingTicketRejectsInconsistentItemStatus() {

        ReadyFixture fixture =
            fixture(
                OrderStatus.PREPARING,
                KitchenTicketStatus.PREPARING,
                KitchenTicketItemStatus.READY,
                KitchenTicketStatus.PREPARING
            );

        assertThatThrownBy(() ->
            service.markReady(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            )
        )
            .isInstanceOfSatisfying(
                KitchenConflictException.class,
                exception ->
                    assertThat(
                        exception.getErrorCode()
                    ).isEqualTo(
                        KitchenErrorCode.CONCURRENT_MODIFICATION
                    )
            )
            .hasMessageContaining(
                "outside PREPARING"
            );

        verify(fixture.target(), never())
            .markReady(
                fixture.at()
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

        Organization organization =
            org.mockito.Mockito.mock(
                Organization.class
            );

        User actor =
            org.mockito.Mockito.mock(
                User.class
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
            service.markReady(
                actorId,
                ticketId,
                OffsetDateTime.parse(
                    "2026-08-29T12:00:00+01:00"
                )
            )
        )
            .isInstanceOf(
                KitchenNotFoundException.class
            );

        verifyNoInteractions(
            orderRepository,
            ticketItemRepository,
            historyRepository
        );
    }

    @Test
    void emptyTicketCannotBecomeReady() {

        ReadyFixture fixture =
            fixture(
                OrderStatus.PREPARING,
                KitchenTicketStatus.PREPARING,
                KitchenTicketItemStatus.PREPARING,
                null
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
            service.markReady(
                fixture.actorId(),
                fixture.ticketId(),
                fixture.at()
            )
        )
            .isInstanceOfSatisfying(
                KitchenConflictException.class,
                exception ->
                    assertThat(
                        exception.getErrorCode()
                    ).isEqualTo(
                        KitchenErrorCode.CONCURRENT_MODIFICATION
                    )
            )
            .hasMessageContaining(
                "without items"
            );

        verify(fixture.target(), never())
            .markReady(
                fixture.at()
            );
    }

    private void assertReplayHasNoMutation(
        ReadyFixture fixture
    ) {
        verify(fixture.target(), never())
            .markReady(
                fixture.at()
            );

        verify(fixture.item(), never())
            .markReady(
                fixture.at()
            );

        verify(ticketItemRepository, never())
            .saveAllAndFlush(
                fixture.items()
            );

        verify(ticketRepository, never())
            .saveAndFlush(
                fixture.target()
            );

        verify(fixture.order(), never())
            .markReady(
                fixture.at()
            );

        verify(orderRepository, never())
            .saveAndFlush(
                fixture.order()
            );

        verifyNoInteractions(
            historyRepository
        );
    }

    private ReadyFixture fixture(
        OrderStatus orderStatus,
        KitchenTicketStatus targetStatus,
        KitchenTicketItemStatus itemStatus,
        KitchenTicketStatus siblingStatus
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
                "2026-08-29T12:00:00+01:00"
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
            target.getOrder()
        ).thenReturn(
            order
        );

        lenient().when(
            target.getStatus()
        ).thenReturn(
            targetStatus
        );

        List<KitchenTicket> siblings =
            new ArrayList<>();

        siblings.add(
            target
        );

        if (siblingStatus != null) {

            KitchenTicket sibling =
                org.mockito.Mockito.mock(
                    KitchenTicket.class
                );

            lenient().when(
                sibling.getId()
            ).thenReturn(
                UUID.randomUUID()
            );

            lenient().when(
                sibling.getOrder()
            ).thenReturn(
                order
            );

            lenient().when(
                sibling.getStatus()
            ).thenReturn(
                siblingStatus
            );

            siblings.add(
                sibling
            );
        }

        lenient().when(
            ticketRepository
                .findAllByOrderForUpdate(
                    orderId,
                    organizationId
                )
        ).thenReturn(
            siblings
        );

        lenient().when(
            item.getKitchenTicket()
        ).thenReturn(
            target
        );

        lenient().when(
            item.getStatus()
        ).thenReturn(
            itemStatus
        );

        List<KitchenTicketItem> items =
            List.of(
                item
            );

        lenient().when(
            ticketItemRepository
                .findAllByTicketForUpdate(
                    ticketId
                )
        ).thenReturn(
            items
        );

        return new ReadyFixture(
            actorId,
            ticketId,
            orderId,
            organizationId,
            at,
            actor,
            order,
            target,
            item,
            List.copyOf(
                siblings
            ),
            items
        );
    }

    private record ReadyFixture(
        UUID actorId,
        UUID ticketId,
        UUID orderId,
        UUID organizationId,
        OffsetDateTime at,
        User actor,
        Order order,
        KitchenTicket target,
        KitchenTicketItem item,
        List<KitchenTicket> siblings,
        List<KitchenTicketItem> items
    ) {
    }
}
