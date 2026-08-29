package com.sup2i.food.kitchen;

import com.sup2i.food.kitchen.domain.KitchenTicket;
import com.sup2i.food.kitchen.domain.KitchenTicketStatus;
import com.sup2i.food.kitchen.service.readiness.KitchenOrderReadiness;
import com.sup2i.food.kitchen.service.readiness.KitchenOrderReadinessDecision;
import com.sup2i.food.kitchen.service.readiness.KitchenOrderReadinessPolicy;
import com.sup2i.food.order.domain.Order;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KitchenOrderReadinessPolicyTest {

    private final KitchenOrderReadinessPolicy policy =
        new KitchenOrderReadinessPolicy();

    @Test
    void allCurrentTicketsReadyMakesOrderReady() {

        UUID orderId =
            UUID.randomUUID();

        Order order =
            order(
                orderId
            );

        KitchenOrderReadinessDecision decision =
            policy.evaluateCurrent(
                orderId,
                List.of(
                    ticket(
                        order,
                        KitchenTicketStatus.READY
                    ),
                    ticket(
                        order,
                        KitchenTicketStatus.READY
                    )
                )
            );

        assertThat(
            decision.readiness()
        ).isEqualTo(
            KitchenOrderReadiness.ORDER_READY
        );

        assertThat(
            decision.totalTickets()
        ).isEqualTo(
            2
        );

        assertThat(
            decision.readyTickets()
        ).isEqualTo(
            2
        );

        assertThat(
            decision.waitingTickets()
        ).isZero();

        assertThat(
            decision.cancelledTickets()
        ).isZero();

        assertThat(
            decision.orderReady()
        ).isTrue();
    }

    @Test
    void readyAndPreparingCurrentTicketsRemainWaiting() {

        UUID orderId =
            UUID.randomUUID();

        Order order =
            order(
                orderId
            );

        KitchenOrderReadinessDecision decision =
            policy.evaluateCurrent(
                orderId,
                List.of(
                    ticket(
                        order,
                        KitchenTicketStatus.READY
                    ),
                    ticket(
                        order,
                        KitchenTicketStatus.PREPARING
                    )
                )
            );

        assertThat(
            decision.readiness()
        ).isEqualTo(
            KitchenOrderReadiness.WAITING
        );

        assertThat(
            decision.readyTickets()
        ).isEqualTo(
            1
        );

        assertThat(
            decision.waitingTickets()
        ).isEqualTo(
            1
        );
    }

    @Test
    void cancellationBlocksCurrentOrderReadiness() {

        UUID orderId =
            UUID.randomUUID();

        Order order =
            order(
                orderId
            );

        KitchenOrderReadinessDecision decision =
            policy.evaluateCurrent(
                orderId,
                List.of(
                    ticket(
                        order,
                        KitchenTicketStatus.READY
                    ),
                    ticket(
                        order,
                        KitchenTicketStatus.CANCELLED
                    )
                )
            );

        assertThat(
            decision.readiness()
        ).isEqualTo(
            KitchenOrderReadiness
                .BLOCKED_BY_CANCELLATION
        );

        assertThat(
            decision.cancelledTickets()
        ).isEqualTo(
            1
        );

        assertThat(
            decision.blockedByCancellation()
        ).isTrue();
    }

    @Test
    void projectingLastPreparingTicketMakesOrderReady() {

        UUID orderId =
            UUID.randomUUID();

        Order order =
            order(
                orderId
            );

        KitchenTicket target =
            ticket(
                order,
                KitchenTicketStatus.PREPARING
            );

        KitchenOrderReadinessDecision decision =
            policy.evaluateAfterTargetReady(
                orderId,
                target.getId(),
                List.of(
                    ticket(
                        order,
                        KitchenTicketStatus.READY
                    ),
                    target
                )
            );

        assertThat(
            decision.readiness()
        ).isEqualTo(
            KitchenOrderReadiness.ORDER_READY
        );

        assertThat(
            decision.readyTickets()
        ).isEqualTo(
            2
        );

        assertThat(
            decision.waitingTickets()
        ).isZero();
    }

    @Test
    void projectingTargetReadyWaitsForAnotherPreparingKitchen() {

        UUID orderId =
            UUID.randomUUID();

        Order order =
            order(
                orderId
            );

        KitchenTicket target =
            ticket(
                order,
                KitchenTicketStatus.PREPARING
            );

        KitchenOrderReadinessDecision decision =
            policy.evaluateAfterTargetReady(
                orderId,
                target.getId(),
                List.of(
                    target,
                    ticket(
                        order,
                        KitchenTicketStatus.PREPARING
                    ),
                    ticket(
                        order,
                        KitchenTicketStatus.READY
                    )
                )
            );

        assertThat(
            decision.readiness()
        ).isEqualTo(
            KitchenOrderReadiness.WAITING
        );

        assertThat(
            decision.readyTickets()
        ).isEqualTo(
            2
        );

        assertThat(
            decision.waitingTickets()
        ).isEqualTo(
            1
        );
    }

    @Test
    void projectingTargetReadyDoesNotIgnoreCancelledSibling() {

        UUID orderId =
            UUID.randomUUID();

        Order order =
            order(
                orderId
            );

        KitchenTicket target =
            ticket(
                order,
                KitchenTicketStatus.PREPARING
            );

        KitchenOrderReadinessDecision decision =
            policy.evaluateAfterTargetReady(
                orderId,
                target.getId(),
                List.of(
                    target,
                    ticket(
                        order,
                        KitchenTicketStatus.CANCELLED
                    )
                )
            );

        assertThat(
            decision.readiness()
        ).isEqualTo(
            KitchenOrderReadiness
                .BLOCKED_BY_CANCELLATION
        );

        assertThat(
            decision.readyTickets()
        ).isEqualTo(
            1
        );

        assertThat(
            decision.cancelledTickets()
        ).isEqualTo(
            1
        );
    }

    @Test
    void invalidSiblingStructureOrProjectionIsRejected() {

        UUID orderId =
            UUID.randomUUID();

        UUID foreignOrderId =
            UUID.randomUUID();

        Order order =
            order(
                orderId
            );

        Order foreignOrder =
            order(
                foreignOrderId
            );

        UUID duplicateId =
            UUID.randomUUID();

        KitchenTicket duplicateA =
            ticket(
                duplicateId,
                order,
                KitchenTicketStatus.READY
            );

        KitchenTicket duplicateB =
            ticket(
                duplicateId,
                order,
                KitchenTicketStatus.PREPARING
            );

        assertThatThrownBy(() ->
            policy.evaluateCurrent(
                orderId,
                List.of(
                    duplicateA,
                    duplicateB
                )
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "duplicate"
            );

        assertThatThrownBy(() ->
            policy.evaluateCurrent(
                orderId,
                List.of(
                    ticket(
                        order,
                        KitchenTicketStatus.READY
                    ),
                    ticket(
                        foreignOrder,
                        KitchenTicketStatus.READY
                    )
                )
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "same order"
            );

        assertThatThrownBy(() ->
            policy.evaluateAfterTargetReady(
                orderId,
                UUID.randomUUID(),
                List.of(
                    ticket(
                        order,
                        KitchenTicketStatus.PREPARING
                    )
                )
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "not part of the order"
            );
    }

    private Order order(
        UUID orderId
    ) {
        Order order =
            mock(
                Order.class
            );

        when(
            order.getId()
        ).thenReturn(
            orderId
        );

        return order;
    }

    private KitchenTicket ticket(
        Order order,
        KitchenTicketStatus status
    ) {
        return ticket(
            UUID.randomUUID(),
            order,
            status
        );
    }

    private KitchenTicket ticket(
        UUID ticketId,
        Order order,
        KitchenTicketStatus status
    ) {
        KitchenTicket ticket =
            mock(
                KitchenTicket.class
            );

        when(
            ticket.getId()
        ).thenReturn(
            ticketId
        );

        when(
            ticket.getOrder()
        ).thenReturn(
            order
        );

        when(
            ticket.getStatus()
        ).thenReturn(
            status
        );

        return ticket;
    }
}
