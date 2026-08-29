package com.sup2i.food.kitchen.service;

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
public class KitchenStartService {

    private static final String PREPARATION_STARTED_REASON =
        "Kitchen preparation started.";

    private final UserRepository userRepository;
    private final KitchenTicketRepository ticketRepository;
    private final KitchenTicketItemRepository ticketItemRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final PreparedStockConsumptionService stockConsumptionService;

    public KitchenStartService(
        UserRepository userRepository,
        KitchenTicketRepository ticketRepository,
        KitchenTicketItemRepository ticketItemRepository,
        OrderRepository orderRepository,
        OrderStatusHistoryRepository historyRepository,
        PreparedStockConsumptionService stockConsumptionService
    ) {
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.ticketItemRepository = ticketItemRepository;
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.stockConsumptionService = stockConsumptionService;
    }

    @Transactional
    public KitchenTicket startTicket(
        UUID actorId,
        UUID ticketId,
        OffsetDateTime at
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(at, "at");

        User actor =
            userRepository
                .findById(actorId)
                .orElseThrow(() ->
                    new BadCredentialsException(
                        "Authenticated user does not exist."
                    )
                );

        if (actor.getOrganization() == null) {
            throw new BadCredentialsException(
                "Authenticated user has no organization."
            );
        }

        UUID organizationId =
            actor.getOrganization().getId();

        /*
         * Discovery is deliberately NON-LOCKING.
         *
         * It only obtains the parent Order id while remaining
         * tenant-safe. The canonical write lock order begins
         * with Order, never with an individual ticket.
         */
        KitchenTicket discovered =
            ticketRepository
                .findOwnedById(
                    ticketId,
                    organizationId
                )
                .orElseThrow(this::ticketNotFound);

        if (
            discovered.getOrder() == null
            || discovered.getOrder().getId() == null
        ) {
            throw concurrentModification(
                "Kitchen ticket has no valid parent order."
            );
        }

        UUID orderId =
            discovered.getOrder().getId();

        /*
         * Canonical lock order:
         *
         * 1. Order
         * 2. every sibling KitchenTicket
         * 3. target KitchenTicketItem rows
         * 4. prepared reservations / balances / lots
         *    inside PreparedStockConsumptionService
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

        if (items.isEmpty()) {
            throw concurrentModification(
                "Kitchen ticket cannot start without items."
            );
        }

        /*
         * State-based replay.
         *
         * Durable HTTP Idempotency-Key persistence is added
         * with B5-C. At the workflow level, a fully progressed
         * target is already a safe no-op.
         */
        if (
            target.getStatus()
                == KitchenTicketStatus.PREPARING
        ) {
            boolean orderPreparing =
                order.getStatus()
                    == OrderStatus.PREPARING;

            boolean allItemsPreparing =
                items.stream()
                    .allMatch(item ->
                        item.getStatus()
                            == KitchenTicketItemStatus.PREPARING
                    );

            if (
                orderPreparing
                && allItemsPreparing
            ) {
                return target;
            }

            throw concurrentModification(
                "Kitchen ticket preparation state is inconsistent."
            );
        }

        boolean ticketStartable =
            target.getStatus()
                == KitchenTicketStatus.QUEUED
            || target.getStatus()
                == KitchenTicketStatus.ACCEPTED;

        if (!ticketStartable) {
            throw new KitchenConflictException(
                KitchenErrorCode.INVALID_KITCHEN_STATUS,
                "Kitchen ticket cannot start from its current status."
            );
        }

        boolean allItemsQueued =
            items.stream()
                .allMatch(item ->
                    item.getStatus()
                        == KitchenTicketItemStatus.QUEUED
                );

        if (!allItemsQueued) {
            throw concurrentModification(
                "Kitchen ticket items are inconsistent with the ticket start state."
            );
        }

        boolean firstGlobalStart =
            order.getStatus()
                == OrderStatus.QUEUED;

        boolean siblingStart =
            order.getStatus()
                == OrderStatus.PREPARING;

        if (
            !firstGlobalStart
            && !siblingStart
        ) {
            throw new KitchenConflictException(
                KitchenErrorCode.INVALID_ORDER_STATUS,
                "Order cannot enter kitchen preparation from its current status."
            );
        }

        /*
         * If the Order is already PREPARING, some other sibling
         * must explain that global state.
         */
        if (siblingStart) {
            boolean progressedSibling =
                siblings.stream()
                    .filter(ticket ->
                        !ticketId.equals(
                            ticket.getId()
                        )
                    )
                    .anyMatch(ticket ->
                        ticket.getStatus()
                            == KitchenTicketStatus.PREPARING
                        || ticket.getStatus()
                            == KitchenTicketStatus.READY
                    );

            if (!progressedSibling) {
                throw concurrentModification(
                    "Order is preparing without another progressed kitchen ticket."
                );
            }
        }

        /*
         * Global stock boundary:
         *
         * only QUEUED -> PREPARING consumes prepared ingredient
         * reservations. A later sibling start never consumes
         * them again.
         */
        if (firstGlobalStart) {
            stockConsumptionService
                .consumePreparedReservations(
                    order,
                    actor,
                    at
                );
        }

        /*
         * Mutate only after every prerequisite has passed.
         */
        target.markPreparing(
            actor,
            at
        );

        for (
            KitchenTicketItem item
            : items
        ) {
            item.markPreparing(
                at
            );
        }

        if (firstGlobalStart) {
            order.markPreparing();

            historyRepository.save(
                new OrderStatusHistory(
                    order,
                    OrderStatus.QUEUED,
                    OrderStatus.PREPARING,
                    actor,
                    PREPARATION_STARTED_REASON,
                    OrderStatusHistorySource.API
                )
            );
        }

        ticketItemRepository
            .saveAllAndFlush(
                items
            );

        ticketRepository
            .saveAndFlush(
                target
            );

        if (firstGlobalStart) {
            orderRepository
                .saveAndFlush(
                    order
                );
        }

        return target;
    }

    private KitchenNotFoundException ticketNotFound() {
        return new KitchenNotFoundException(
            KitchenErrorCode.KITCHEN_TICKET_NOT_FOUND,
            "Kitchen ticket does not exist."
        );
    }

    private KitchenConflictException concurrentModification(
        String message
    ) {
        return new KitchenConflictException(
            KitchenErrorCode.CONCURRENT_MODIFICATION,
            message
        );
    }
}
