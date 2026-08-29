package com.sup2i.food.kitchen.service;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.kitchen.api.KitchenTicketResponseMapper;
import com.sup2i.food.kitchen.api.dto.KitchenTicketResponse;
import com.sup2i.food.kitchen.domain.KitchenTicket;
import com.sup2i.food.kitchen.domain.KitchenTicketItem;
import com.sup2i.food.kitchen.domain.KitchenTicketStatus;
import com.sup2i.food.kitchen.repository.KitchenTicketItemRepository;
import com.sup2i.food.kitchen.repository.KitchenTicketRepository;
import com.sup2i.food.order.domain.OrderItemMenuSelection;
import com.sup2i.food.order.repository.OrderItemMenuSelectionRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class KitchenQueryService {

    private final UserRepository userRepository;

    private final KitchenTicketRepository
        ticketRepository;

    private final KitchenTicketItemRepository
        ticketItemRepository;

    private final OrderItemMenuSelectionRepository
        menuSelectionRepository;

    private final KitchenTicketResponseMapper
        responseMapper;

    public KitchenQueryService(
        UserRepository userRepository,
        KitchenTicketRepository ticketRepository,
        KitchenTicketItemRepository ticketItemRepository,
        OrderItemMenuSelectionRepository menuSelectionRepository,
        KitchenTicketResponseMapper responseMapper
    ) {
        this.userRepository =
            userRepository;

        this.ticketRepository =
            ticketRepository;

        this.ticketItemRepository =
            ticketItemRepository;

        this.menuSelectionRepository =
            menuSelectionRepository;

        this.responseMapper =
            responseMapper;
    }

    @Transactional(readOnly = true)
    public List<KitchenTicketResponse> listTickets(
        UUID actorId,
        UUID kitchenLocationId,
        KitchenTicketStatus status
    ) {

        Objects.requireNonNull(
            actorId,
            "actorId"
        );

        Objects.requireNonNull(
            kitchenLocationId,
            "kitchenLocationId"
        );

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
            actor
                .getOrganization()
                .getId();

        EnumSet<KitchenTicketStatus> statuses =
            status == null
                ? EnumSet.allOf(
                    KitchenTicketStatus.class
                )
                : EnumSet.of(status);

        List<KitchenTicket> tickets =
            ticketRepository.findQueue(
                organizationId,
                kitchenLocationId,
                statuses
            );

        if (tickets.isEmpty()) {
            return List.of();
        }

        List<UUID> ticketIds =
            tickets
                .stream()
                .map(KitchenTicket::getId)
                .toList();

        List<UUID> orderIds =
            tickets
                .stream()
                .map(ticket ->
                    ticket
                        .getOrder()
                        .getId()
                )
                .distinct()
                .toList();

        List<KitchenTicketItem> allLines =
            ticketItemRepository
                .findAllByTicketIds(
                    ticketIds
                );

        List<OrderItemMenuSelection> allSelections =
            menuSelectionRepository
                .findAllByOrderIds(
                    orderIds
                );

        Map<UUID, List<KitchenTicketItem>> linesByTicket =
            new HashMap<>();

        for (
            KitchenTicketItem line
            : allLines
        ) {

            UUID ticketId =
                line
                    .getKitchenTicket()
                    .getId();

            linesByTicket
                .computeIfAbsent(
                    ticketId,
                    ignored ->
                        new ArrayList<>()
                )
                .add(line);
        }

        Map<UUID, OrderItemMenuSelection> selectionsById =
            new LinkedHashMap<>();

        for (
            OrderItemMenuSelection selection
            : allSelections
        ) {

            OrderItemMenuSelection previous =
                selectionsById.put(
                    selection.getId(),
                    selection
                );

            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate menu selection identifier."
                );
            }
        }

        return tickets
            .stream()
            .map(ticket ->
                responseMapper.toResponse(
                    ticket,
                    linesByTicket.getOrDefault(
                        ticket.getId(),
                        List.of()
                    ),
                    selectionsById
                )
            )
            .toList();
    }
}
