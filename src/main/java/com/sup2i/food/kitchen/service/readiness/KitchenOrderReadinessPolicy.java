package com.sup2i.food.kitchen.service.readiness;

import com.sup2i.food.kitchen.domain.KitchenTicket;
import com.sup2i.food.kitchen.domain.KitchenTicketStatus;
import com.sup2i.food.order.domain.Order;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class KitchenOrderReadinessPolicy {

    public KitchenOrderReadinessDecision evaluateCurrent(
        UUID orderId,
        List<KitchenTicket> tickets
    ) {
        return evaluate(
            orderId,
            null,
            tickets,
            false
        );
    }

    public KitchenOrderReadinessDecision
        evaluateAfterTargetReady(
            UUID orderId,
            UUID targetTicketId,
            List<KitchenTicket> tickets
        ) {

        Objects.requireNonNull(
            targetTicketId,
            "targetTicketId"
        );

        return evaluate(
            orderId,
            targetTicketId,
            tickets,
            true
        );
    }

    private KitchenOrderReadinessDecision evaluate(
        UUID orderId,
        UUID projectedTargetTicketId,
        List<KitchenTicket> tickets,
        boolean projectTargetReady
    ) {
        Objects.requireNonNull(
            orderId,
            "orderId"
        );

        Objects.requireNonNull(
            tickets,
            "tickets"
        );

        if (tickets.isEmpty()) {
            throw new IllegalArgumentException(
                "Kitchen readiness requires at least one ticket."
            );
        }

        Set<UUID> ticketIds =
            new HashSet<>();

        int readyTickets =
            0;

        int waitingTickets =
            0;

        int cancelledTickets =
            0;

        boolean targetFound =
            false;

        for (
            KitchenTicket ticket
            : tickets
        ) {
            if (ticket == null) {
                throw new IllegalArgumentException(
                    "Kitchen readiness cannot evaluate a null ticket."
                );
            }

            UUID ticketId =
                Objects.requireNonNull(
                    ticket.getId(),
                    "ticket.id"
                );

            boolean uniqueTicket =
                ticketIds.add(
                    ticketId
                );

            if (!uniqueTicket) {
                throw new IllegalArgumentException(
                    "Kitchen readiness contains a duplicate ticket identifier."
                );
            }

            Order ticketOrder =
                Objects.requireNonNull(
                    ticket.getOrder(),
                    "ticket.order"
                );

            UUID ticketOrderId =
                Objects.requireNonNull(
                    ticketOrder.getId(),
                    "ticket.order.id"
                );

            if (
                !orderId.equals(
                    ticketOrderId
                )
            ) {
                throw new IllegalArgumentException(
                    "Kitchen readiness tickets must belong to the same order."
                );
            }

            KitchenTicketStatus status =
                Objects.requireNonNull(
                    ticket.getStatus(),
                    "ticket.status"
                );

            if (
                projectTargetReady
                && ticketId.equals(
                    projectedTargetTicketId
                )
            ) {
                targetFound =
                    true;

                if (
                    status
                        != KitchenTicketStatus.PREPARING
                ) {
                    throw new IllegalArgumentException(
                        "Only a PREPARING kitchen ticket can be projected to READY."
                    );
                }

                status =
                    KitchenTicketStatus.READY;
            }

            switch (status) {

                case READY ->
                    readyTickets++;

                case CANCELLED ->
                    cancelledTickets++;

                case QUEUED,
                     ACCEPTED,
                     PREPARING ->
                    waitingTickets++;
            }
        }

        if (
            projectTargetReady
            && !targetFound
        ) {
            throw new IllegalArgumentException(
                "Projected kitchen ticket is not part of the order."
            );
        }

        KitchenOrderReadiness readiness;

        /*
         * Conservative cancellation rule:
         *
         * B5-B3 does not interpret cancellation as successful
         * preparation. A separate cancellation/remake/refund
         * workflow must resolve that business state.
         */
        if (cancelledTickets > 0) {
            readiness =
                KitchenOrderReadiness
                    .BLOCKED_BY_CANCELLATION;
        }
        else if (
            readyTickets
                == tickets.size()
        ) {
            readiness =
                KitchenOrderReadiness
                    .ORDER_READY;
        }
        else {
            readiness =
                KitchenOrderReadiness
                    .WAITING;
        }

        return new KitchenOrderReadinessDecision(
            readiness,
            tickets.size(),
            readyTickets,
            waitingTickets,
            cancelledTickets
        );
    }
}
