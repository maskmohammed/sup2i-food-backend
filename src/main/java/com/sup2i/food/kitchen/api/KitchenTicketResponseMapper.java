package com.sup2i.food.kitchen.api;

import com.sup2i.food.kitchen.api.dto.KitchenTicketLineResponse;
import com.sup2i.food.kitchen.api.dto.KitchenTicketResponse;
import com.sup2i.food.kitchen.domain.KitchenTicket;
import com.sup2i.food.kitchen.domain.KitchenTicketItem;
import com.sup2i.food.order.api.dto.OrderItemResponse;
import com.sup2i.food.order.domain.OrderItem;
import com.sup2i.food.order.domain.OrderItemMenuSelection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class KitchenTicketResponseMapper {

    public KitchenTicketResponse toResponse(
        KitchenTicket ticket,
        List<KitchenTicketItem> ticketItems,
        Map<UUID, OrderItemMenuSelection> selections
    ) {

        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(ticketItems, "ticketItems");
        Objects.requireNonNull(selections, "selections");

        if (ticketItems.isEmpty()) {
            throw new IllegalStateException(
                "Kitchen ticket has no routed lines."
            );
        }

        LinkedHashMap<UUID, OrderItem> uniqueOrderItems =
            new LinkedHashMap<>();

        List<KitchenTicketLineResponse> lines =
            new ArrayList<>();

        for (
            KitchenTicketItem ticketItem
            : ticketItems
        ) {

            validateTicketOwnership(
                ticket,
                ticketItem
            );

            OrderItem orderItem =
                ticketItem.getOrderItem();

            if (
                orderItem == null
                || orderItem.getId() == null
            ) {
                throw new IllegalStateException(
                    "Kitchen ticket line has no valid order item."
                );
            }

            uniqueOrderItems.putIfAbsent(
                orderItem.getId(),
                orderItem
            );

            lines.add(
                toLineResponse(
                    ticketItem,
                    orderItem,
                    selections
                )
            );
        }

        List<OrderItemResponse> items =
            uniqueOrderItems
                .values()
                .stream()
                .map(this::toOrderItemResponse)
                .toList();

        return new KitchenTicketResponse(
            ticket.getId(),
            ticket.getOrder().getId(),
            ticket.getOrder().getOrderNumber(),
            ticket.getStatus(),
            ticket.getPriority(),
            ticket.getQueuedAt(),
            ticket.getStartedAt(),
            ticket.getReadyAt(),
            items,
            List.copyOf(lines)
        );
    }

    private KitchenTicketLineResponse toLineResponse(
        KitchenTicketItem ticketItem,
        OrderItem orderItem,
        Map<UUID, OrderItemMenuSelection> selections
    ) {

        UUID menuSelectionId =
            ticketItem.getMenuSelectionId();

        UUID productId;
        UUID variantId;
        String productName;
        String variantName;

        if (menuSelectionId == null) {

            productId =
                orderItem
                    .getProduct()
                    .getId();

            variantId =
                orderItem.getVariant() == null
                    ? null
                    : orderItem
                        .getVariant()
                        .getId();

            productName =
                orderItem
                    .getProductNameSnapshot();

            variantName =
                orderItem
                    .getVariantNameSnapshot();

        } else {

            OrderItemMenuSelection selection =
                selections.get(
                    menuSelectionId
                );

            if (selection == null) {
                throw new IllegalStateException(
                    "Kitchen ticket line references a missing menu selection."
                );
            }

            boolean coherent =
                selection.getOrderItem() != null
                && orderItem.getId().equals(
                    selection
                        .getOrderItem()
                        .getId()
                );

            if (!coherent) {
                throw new IllegalStateException(
                    "Kitchen menu selection belongs to another order item."
                );
            }

            productId =
                selection
                    .getProduct()
                    .getId();

            variantId =
                selection.getVariant() == null
                    ? null
                    : selection
                        .getVariant()
                        .getId();

            productName =
                selection
                    .getProductNameSnapshot();

            variantName =
                selection
                    .getVariantNameSnapshot();
        }

        return new KitchenTicketLineResponse(
            ticketItem.getId(),
            orderItem.getId(),
            menuSelectionId,
            productId,
            variantId,
            productName,
            variantName,
            ticketItem.getQuantity(),
            ticketItem.getStatus(),
            orderItem.getSpecialInstructions(),
            ticketItem.getStartedAt(),
            ticketItem.getReadyAt(),
            ticketItem.getCancelledAt(),
            ticketItem.getIssueNote()
        );
    }

    private OrderItemResponse toOrderItemResponse(
        OrderItem item
    ) {

        return new OrderItemResponse(
            item.getId(),
            item.getProduct().getId(),
            item.getVariant() == null
                ? null
                : item.getVariant().getId(),
            item.getProductNameSnapshot(),
            item.getVariantNameSnapshot(),
            item.getSkuSnapshot(),
            item.getUnitPrice(),
            item.getQuantity(),
            item.getDiscountAmount(),
            item.getLineTotal(),
            item.getTaxRateSnapshot(),
            item.getLineTax(),
            item.getSpecialInstructions()
        );
    }

    private void validateTicketOwnership(
        KitchenTicket ticket,
        KitchenTicketItem ticketItem
    ) {

        boolean coherent =
            ticketItem.getKitchenTicket() != null
            && ticket.getId().equals(
                ticketItem
                    .getKitchenTicket()
                    .getId()
            );

        if (!coherent) {
            throw new IllegalStateException(
                "Kitchen ticket line belongs to another ticket."
            );
        }
    }
}
