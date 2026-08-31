package com.sup2i.food.kitchen.service;
import org.springframework.beans.factory.annotation.Autowired;
import com.sup2i.food.notification.service.OrderNotificationDispatchService;

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
import com.sup2i.food.kitchen.service.readiness.KitchenOrderReadiness;
import com.sup2i.food.kitchen.service.readiness.KitchenOrderReadinessDecision;
import com.sup2i.food.kitchen.service.readiness.KitchenOrderReadinessPolicy;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.domain.OrderStatusHistory;
import com.sup2i.food.order.domain.OrderStatusHistorySource;
import com.sup2i.food.order.repository.OrderRepository;
import com.sup2i.food.order.repository.OrderStatusHistoryRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class KitchenReadyService {

    private static final String ORDER_READY_REASON =
        "Order ready for collection.";

    private final UserRepository userRepository;

    private final KitchenTicketRepository ticketRepository;

    private final KitchenTicketItemRepository
        ticketItemRepository;

    private final OrderRepository orderRepository;

    private final OrderStatusHistoryRepository
        historyRepository;

    private final KitchenOrderReadinessPolicy
        readinessPolicy =
            new KitchenOrderReadinessPolicy();
    private OrderNotificationDispatchService
        notificationDispatchService;

    @Autowired
    void setNotificationDispatchService(
        OrderNotificationDispatchService notificationDispatchService
    ) {
        this.notificationDispatchService =
            notificationDispatchService;
    }


    public KitchenReadyService(
        UserRepository userRepository,
        KitchenTicketRepository ticketRepository,
        KitchenTicketItemRepository ticketItemRepository,
        OrderRepository orderRepository,
        OrderStatusHistoryRepository historyRepository
    ) {
        this.userRepository =
            userRepository;

        this.ticketRepository =
            ticketRepository;

        this.ticketItemRepository =
            ticketItemRepository;

        this.orderRepository =
            orderRepository;

        this.historyRepository =
            historyRepository;
    }

    @Transactional
    public KitchenTicket markReady(
        UUID actorId,
        UUID ticketId,
        OffsetDateTime at
    ) {
        Objects.requireNonNull(
            actorId,
            "actorId"
        );

        Objects.requireNonNull(
            ticketId,
            "ticketId"
        );

        Objects.requireNonNull(
            at,
            "at"
        );

        User actor =
            userRepository
                .findById(
                    actorId
                )
                .orElseThrow(() ->
                    new BadCredentialsException(
                        "Authenticated user does not exist."
                    )
                );

        if (
            actor.getOrganization()
                == null
        ) {
            throw new BadCredentialsException(
                "Authenticated user has no organization."
            );
        }

        UUID organizationId =
            actor
                .getOrganization()
                .getId();

        /*
         * Non-locking tenant-safe discovery only.
         * Canonical write locking begins with the Order.
         */
        KitchenTicket discovered =
            ticketRepository
                .findOwnedById(
                    ticketId,
                    organizationId
                )
                .orElseThrow(
                    this::ticketNotFound
                );

        if (
            discovered.getOrder()
                == null
            || discovered
                .getOrder()
                .getId()
                == null
        ) {
            throw concurrentModification(
                "Kitchen ticket has no valid parent order."
            );
        }

        UUID orderId =
            discovered
                .getOrder()
                .getId();

        /*
         * Canonical lock order:
         *
         * 1. Order
         * 2. every sibling KitchenTicket
         * 3. target KitchenTicketItem rows
         */
        Order order =
            orderRepository
                .findOwnedByIdForUpdate(
                    orderId,
                    organizationId
                )
                .orElseThrow(() ->
                    concurrentModification(
                        "Kitchen ticket parent order disappeared."
                    )
                );

        List<KitchenTicket> siblings =
            ticketRepository
                .findAllByOrderForUpdate(
                    orderId,
                    organizationId
                );

        KitchenTicket target =
            siblings
                .stream()
                .filter(ticket ->
                    ticketId.equals(
                        ticket.getId()
                    )
                )
                .findFirst()
                .orElseThrow(() ->
                    concurrentModification(
                        "Kitchen ticket disappeared while acquiring workflow locks."
                    )
                );

        List<KitchenTicketItem> items =
            ticketItemRepository
                .findAllByTicketForUpdate(
                    ticketId
                );

        validateTargetItems(
            ticketId,
            items
        );

        /*
         * State-based workflow replay.
         *
         * Durable Idempotency-Key persistence belongs to B5-C.
         */
        if (
            target.getStatus()
                == KitchenTicketStatus.READY
        ) {
            return replayReadyTicket(
                order,
                target,
                siblings,
                items
            );
        }

        if (
            target.getStatus()
                != KitchenTicketStatus.PREPARING
        ) {
            throw new KitchenConflictException(
                KitchenErrorCode.INVALID_KITCHEN_STATUS,
                "Only a preparing kitchen ticket can become ready."
            );
        }

        if (
            order.getStatus()
                != OrderStatus.PREPARING
        ) {
            throw new KitchenConflictException(
                KitchenErrorCode.INVALID_ORDER_STATUS,
                "Only a preparing order can receive a ready kitchen ticket."
            );
        }

        boolean everyItemPreparing =
            items
                .stream()
                .allMatch(item ->
                    item.getStatus()
                        == KitchenTicketItemStatus.PREPARING
                );

        if (!everyItemPreparing) {
            throw concurrentModification(
                "Preparing kitchen ticket contains an item outside PREPARING."
            );
        }

        /*
         * Prospective aggregate decision BEFORE mutation.
         */
        KitchenOrderReadinessDecision decision =
            readinessPolicy
                .evaluateAfterTargetReady(
                    orderId,
                    ticketId,
                    siblings
                );

        if (
            decision.blockedByCancellation()
        ) {
            throw concurrentModification(
                "Kitchen readiness is blocked by a cancelled sibling ticket."
            );
        }

        /*
         * No business mutation occurs before all validation
         * and aggregate decision work has succeeded.
         */
        target.markReady(
            at
        );

        for (
            KitchenTicketItem item
            : items
        ) {
            item.markReady(
                at
            );
        }

        /*
         * Persist the Kitchen sub-aggregate first.
         */
        ticketItemRepository
            .saveAllAndFlush(
                items
            );

        ticketRepository
            .saveAndFlush(
                target
            );

        /*
         * Only the final ready kitchen moves the global Order.
         */
        if (
            decision.orderReady()
        ) {
            order.markReady(
                at
            );

            if (notificationDispatchService != null) {
                notificationDispatchService
                    .orderReadyAfterCommit(
                        order
                    );
            }

            orderRepository
                .saveAndFlush(
                    order
                );

            historyRepository
                .saveAndFlush(
                    new OrderStatusHistory(
                        order,
                        OrderStatus.PREPARING,
                        OrderStatus.READY,
                        actor,
                        ORDER_READY_REASON,
                        OrderStatusHistorySource.API
                    )
                );
        }

        return target;
    }

    private KitchenTicket replayReadyTicket(
        Order order,
        KitchenTicket target,
        List<KitchenTicket> siblings,
        List<KitchenTicketItem> items
    ) {
        boolean everyItemReady =
            items
                .stream()
                .allMatch(item ->
                    item.getStatus()
                        == KitchenTicketItemStatus.READY
                );

        if (!everyItemReady) {
            throw concurrentModification(
                "Ready kitchen ticket contains an item outside READY."
            );
        }

        KitchenOrderReadinessDecision decision =
            readinessPolicy
                .evaluateCurrent(
                    order.getId(),
                    siblings
                );

        if (
            decision.blockedByCancellation()
        ) {
            throw concurrentModification(
                "Ready kitchen workflow is blocked by a cancelled sibling ticket."
            );
        }

        if (
            order.getStatus()
                == OrderStatus.PREPARING
        ) {
            /*
             * Partial replay:
             * this ticket is READY but another kitchen still
             * has work to complete.
             */
            if (
                decision.readiness()
                    == KitchenOrderReadiness.WAITING
            ) {
                return target;
            }

            /*
             * All tickets READY + Order PREPARING is not
             * repaired silently.
             */
            throw concurrentModification(
                "All kitchen tickets are ready but order is still preparing."
            );
        }

        if (
            order.getStatus()
                == OrderStatus.READY
        ) {
            if (
                decision.orderReady()
            ) {
                return target;
            }

            throw concurrentModification(
                "Order is ready while kitchen workflow is still incomplete."
            );
        }

        throw new KitchenConflictException(
            KitchenErrorCode.INVALID_ORDER_STATUS,
            "Ready kitchen ticket is incompatible with current order status."
        );
    }

    private void validateTargetItems(
        UUID ticketId,
        List<KitchenTicketItem> items
    ) {
        if (
            items == null
            || items.isEmpty()
        ) {
            throw concurrentModification(
                "Kitchen ticket cannot become ready without items."
            );
        }

        for (
            KitchenTicketItem item
            : items
        ) {
            if (
                item == null
                || item.getKitchenTicket()
                    == null
                || item
                    .getKitchenTicket()
                    .getId()
                    == null
                || !ticketId.equals(
                    item
                        .getKitchenTicket()
                        .getId()
                )
            ) {
                throw concurrentModification(
                    "Kitchen ticket item does not belong to the target ticket."
                );
            }
        }
    }

    private KitchenNotFoundException ticketNotFound() {

        return new KitchenNotFoundException(
            KitchenErrorCode.KITCHEN_TICKET_NOT_FOUND,
            "Kitchen ticket does not exist."
        );
    }

    private KitchenConflictException
        concurrentModification(
            String message
        ) {

        return new KitchenConflictException(
            KitchenErrorCode.CONCURRENT_MODIFICATION,
            message
        );
    }
}
