package com.sup2i.food.kitchen.service;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.kitchen.api.dto.KitchenTicketItemResponse;
import com.sup2i.food.kitchen.api.dto.KitchenTicketMutationResponse;
import com.sup2i.food.kitchen.api.dto.KitchenTicketResponse;
import com.sup2i.food.kitchen.domain.KitchenTicket;
import com.sup2i.food.kitchen.domain.KitchenTicketItem;
import com.sup2i.food.kitchen.domain.KitchenTicketStatus;
import com.sup2i.food.kitchen.exception.KitchenTicketConflictException;
import com.sup2i.food.kitchen.exception.KitchenTicketNotFoundException;
import com.sup2i.food.kitchen.repository.KitchenTicketItemRepository;
import com.sup2i.food.kitchen.repository.KitchenTicketRepository;
import com.sup2i.food.notification.service.NotificationService;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderItem;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.domain.OrderStatusHistory;
import com.sup2i.food.order.domain.OrderStatusHistorySource;
import com.sup2i.food.order.repository.OrderItemRepository;
import com.sup2i.food.order.repository.OrderRepository;
import com.sup2i.food.order.repository.OrderStatusHistoryRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class KitchenTicketService {

    private final KitchenTicketRepository kitchenTicketRepository;
    private final KitchenTicketItemRepository kitchenTicketItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public KitchenTicketService(
        KitchenTicketRepository kitchenTicketRepository,
        KitchenTicketItemRepository kitchenTicketItemRepository,
        OrderItemRepository orderItemRepository,
        OrderRepository orderRepository,
        OrderStatusHistoryRepository orderStatusHistoryRepository,
        UserRepository userRepository,
        NotificationService notificationService
    ) {
        this.kitchenTicketRepository =
            kitchenTicketRepository;

        this.kitchenTicketItemRepository =
            kitchenTicketItemRepository;

        this.orderItemRepository =
            orderItemRepository;

        this.orderRepository =
            orderRepository;

        this.orderStatusHistoryRepository =
            orderStatusHistoryRepository;

        this.userRepository =
            userRepository;

        this.notificationService =
            notificationService;
    }

    @Transactional
    public KitchenTicket createTicketForPaidOrder(
        Order order
    ) {

        KitchenTicket ticket =
            new KitchenTicket(
                order,
                order.getLocation()
            );

        kitchenTicketRepository
            .saveAndFlush(ticket);

        List<OrderItem> items =
            orderItemRepository
                .findAllByOrder_IdOrderByIdAsc(
                    order.getId()
                );

        for (
            OrderItem item
            : items
        ) {

            kitchenTicketItemRepository
                .save(
                    new KitchenTicketItem(
                        ticket,
                        item,
                        BigDecimal
                            .valueOf(
                                item.getQuantity()
                            )
                    )
                );
        }

        kitchenTicketItemRepository
            .flush();

        return ticket;
    }

    @Transactional
    public KitchenTicketMutationResponse startPreparation(
        UUID actorId,
        UUID ticketId
    ) {

        User actor =
            resolveActor(actorId);

        KitchenTicket ticket =
            ownedTicketForUpdate(
                ticketId,
                actor
            );

        if (
            ticket.getStatus()
                == KitchenTicketStatus.PREPARING
        ) {
            return new KitchenTicketMutationResponse(
                response(ticket),
                true
            );
        }

        if (
            ticket.getStatus()
                != KitchenTicketStatus.QUEUED
        ) {
            throw new KitchenTicketConflictException(
                "Only a QUEUED ticket can start preparation."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        ticket.startPreparation(
            actor,
            now
        );

        kitchenTicketRepository
            .saveAndFlush(ticket);

        for (
            KitchenTicketItem item
            : kitchenTicketItemRepository
                .findAllByKitchenTicket_IdOrderByIdAsc(
                    ticket.getId()
                )
        ) {
            item.startPreparation(now);
        }

        kitchenTicketItemRepository
            .flush();

        syncOrderStatus(
            ticket.getOrder()
                .getId(),
            OrderStatus.QUEUED,
            OrderStatus.PREPARING,
            List.of(
                KitchenTicketStatus.PREPARING,
                KitchenTicketStatus.READY
            ),
            actor
        );

        return new KitchenTicketMutationResponse(
            response(ticket),
            false
        );
    }

    @Transactional
    public KitchenTicketMutationResponse markReady(
        UUID actorId,
        UUID ticketId
    ) {

        User actor =
            resolveActor(actorId);

        KitchenTicket ticket =
            ownedTicketForUpdate(
                ticketId,
                actor
            );

        if (
            ticket.getStatus()
                == KitchenTicketStatus.READY
        ) {
            return new KitchenTicketMutationResponse(
                response(ticket),
                true
            );
        }

        if (
            ticket.getStatus()
                != KitchenTicketStatus.PREPARING
        ) {
            throw new KitchenTicketConflictException(
                "Only a PREPARING ticket can be marked ready."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        ticket.markReady(now);

        kitchenTicketRepository
            .saveAndFlush(ticket);

        for (
            KitchenTicketItem item
            : kitchenTicketItemRepository
                .findAllByKitchenTicket_IdOrderByIdAsc(
                    ticket.getId()
                )
        ) {
            item.markReady(now);
        }

        kitchenTicketItemRepository
            .flush();

        syncOrderStatus(
            ticket.getOrder()
                .getId(),
            OrderStatus.PREPARING,
            OrderStatus.READY,
            List.of(
                KitchenTicketStatus.READY
            ),
            actor
        );

        return new KitchenTicketMutationResponse(
            response(ticket),
            false
        );
    }

    @Transactional(readOnly = true)
    public List<KitchenTicketResponse> queue(
        UUID actorId
    ) {

        User actor =
            resolveActor(actorId);

        List<KitchenTicket> tickets =
            kitchenTicketRepository
                .findQueue(
                    actor.getOrganization()
                        .getId(),
                    List.of(
                        KitchenTicketStatus.QUEUED,
                        KitchenTicketStatus.ACCEPTED,
                        KitchenTicketStatus.PREPARING
                    )
                );

        return tickets.stream()
            .map(this::response)
            .toList();
    }

    private void syncOrderStatus(
        UUID orderId,
        OrderStatus expectedCurrent,
        OrderStatus target,
        List<KitchenTicketStatus> acceptableTicketStatuses,
        User actor
    ) {

        List<KitchenTicket> siblings =
            kitchenTicketRepository
                .findAllByOrder_Id(
                    orderId
                );

        List<KitchenTicket> active =
            siblings.stream()
                .filter(t ->
                    t.getStatus()
                        != KitchenTicketStatus.CANCELLED
                )
                .toList();

        if (active.isEmpty()) {
            return;
        }

        boolean allReached =
            active.stream()
                .allMatch(t ->
                    acceptableTicketStatuses
                        .contains(
                            t.getStatus()
                        )
                );

        if (!allReached) {
            return;
        }

        Order order =
            orderRepository
                .findById(orderId)
                .orElseThrow(() ->
                    new KitchenTicketNotFoundException(
                        "Order does not exist."
                    )
                );

        if (
            order.getStatus()
                != expectedCurrent
        ) {
            return;
        }

        if (
            target
                == OrderStatus.PREPARING
        ) {
            order.markPreparing();
        } else if (
            target
                == OrderStatus.READY
        ) {
            order.markReady(
                OffsetDateTime.now()
            );
        }

        orderStatusHistoryRepository
            .save(
                new OrderStatusHistory(
                    order,
                    expectedCurrent,
                    target,
                    actor,
                    "Kitchen ticket "
                        + target
                            .name()
                            .toLowerCase(),
                    OrderStatusHistorySource
                        .API
                )
            );

        orderRepository
            .saveAndFlush(order);

        if (
            target
                == OrderStatus.READY
        ) {
            notificationService
                .notifyOrderReady(order);
        }
    }

    private KitchenTicket ownedTicketForUpdate(
        UUID ticketId,
        User actor
    ) {

        return kitchenTicketRepository
            .findOwnedByIdForUpdate(
                ticketId,
                actor.getOrganization()
                    .getId()
            )
            .orElseThrow(() ->
                new KitchenTicketNotFoundException(
                    "Kitchen ticket does not exist."
                )
            );
    }

    private User resolveActor(
        UUID actorId
    ) {

        User actor =
            userRepository
                .findById(actorId)
                .orElseThrow(() ->
                    new BadCredentialsException(
                        "Authenticated user does not exist."
                    )
                );

        if (
            !actor.getOrganization()
                .isActive()
        ) {
            throw new KitchenTicketConflictException(
                "Organization is inactive."
            );
        }

        return actor;
    }

    private KitchenTicketResponse response(
        KitchenTicket ticket
    ) {

        List<KitchenTicketItemResponse> items =
            kitchenTicketItemRepository
                .findAllByKitchenTicket_IdOrderByIdAsc(
                    ticket.getId()
                )
                .stream()
                .map(item ->
                    new KitchenTicketItemResponse(
                        item.getId(),
                        item.getOrderItem()
                            .getId(),
                        item.getOrderItem()
                            .getProductNameSnapshot(),
                        item.getQuantity(),
                        item.getStatus()
                            .name()
                    )
                )
                .toList();

        return new KitchenTicketResponse(
            ticket.getId(),
            ticket.getOrder()
                .getId(),
            ticket.getOrder()
                .getOrderNumber(),
            ticket.getKitchenLocation()
                .getId(),
            ticket.getStatus()
                .name(),
            ticket.getPriority(),
            ticket.getQueuedAt(),
            ticket.getAcceptedAt(),
            ticket.getStartedAt(),
            ticket.getReadyAt(),
            items
        );
    }
}
