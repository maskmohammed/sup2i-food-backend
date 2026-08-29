package com.sup2i.food.kitchen.api;

import com.sup2i.food.kitchen.api.dto.KitchenTicketResponse;
import com.sup2i.food.kitchen.domain.KitchenTicketStatus;
import com.sup2i.food.kitchen.service.KitchenCommandService;
import com.sup2i.food.kitchen.service.KitchenQueryService;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping(
    "/api/v1/kitchen/tickets"
)
public class KitchenController {

    private final KitchenQueryService queryService;

    private final KitchenCommandService commandService;

    public KitchenController(
        KitchenQueryService queryService,
        KitchenCommandService commandService
    ) {
        this.queryService =
            queryService;

        this.commandService =
            commandService;
    }

    @GetMapping
    @PreAuthorize(
        "hasAuthority('kitchen.read')"
    )
    public List<KitchenTicketResponse> listTickets(
        @RequestParam(
            name = "kitchenLocationId"
        )
        UUID kitchenLocationId,

        @RequestParam(
            name = "status",
            required = false
        )
        KitchenTicketStatus status,

        JwtAuthenticationToken authentication
    ) {

        return queryService.listTickets(
            actorId(authentication),
            kitchenLocationId,
            status
        );
    }

    @PostMapping(
        "/{ticketId}/start"
    )
    @PreAuthorize(
        "hasAuthority('kitchen.prepare')"
    )
    public KitchenTicketResponse startTicket(
        @PathVariable
        UUID ticketId,

        @NotBlank
        @Size(
            min = 8,
            max = 160
        )
        @RequestHeader(
            name = "Idempotency-Key",
            required = true
        )
        String idempotencyKey,

        JwtAuthenticationToken authentication
    ) {

        return commandService.startTicket(
            actorId(authentication),
            ticketId,
            idempotencyKey
        );
    }

    @PostMapping(
        "/{ticketId}/ready"
    )
    @PreAuthorize(
        "hasAuthority('kitchen.ready')"
    )
    public KitchenTicketResponse markReady(
        @PathVariable
        UUID ticketId,

        @NotBlank
        @Size(
            min = 8,
            max = 160
        )
        @RequestHeader(
            name = "Idempotency-Key",
            required = true
        )
        String idempotencyKey,

        JwtAuthenticationToken authentication
    ) {

        return commandService.markReady(
            actorId(authentication),
            ticketId,
            idempotencyKey
        );
    }

    private UUID actorId(
        JwtAuthenticationToken authentication
    ) {

        try {

            return UUID.fromString(
                authentication
                    .getToken()
                    .getSubject()
            );

        } catch (
            IllegalArgumentException
            | NullPointerException exception
        ) {

            throw new BadCredentialsException(
                "Invalid authenticated user identifier.",
                exception
            );
        }
    }
}
