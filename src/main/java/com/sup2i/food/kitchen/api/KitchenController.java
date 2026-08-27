package com.sup2i.food.kitchen.api;

import com.sup2i.food.kitchen.api.dto.KitchenTicketMutationResponse;
import com.sup2i.food.kitchen.api.dto.KitchenTicketResponse;
import com.sup2i.food.kitchen.service.KitchenTicketService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(
    name = "Kitchen",
    description = "Kitchen display queue and ticket preparation workflow."
)
@RestController
@RequestMapping("/api/v1/kitchen")
@PreAuthorize("isAuthenticated()")
public class KitchenController {

    private final KitchenTicketService service;

    public KitchenController(
        KitchenTicketService service
    ) {
        this.service =
            service;
    }

    @GetMapping("/queue")
    public List<KitchenTicketResponse> queue(
        JwtAuthenticationToken authentication
    ) {

        return service.queue(
            userId(authentication)
        );
    }

    @PostMapping("/tickets/{ticketId}/start-preparation")
    public KitchenTicketMutationResponse startPreparation(
        @PathVariable UUID ticketId,
        JwtAuthenticationToken authentication
    ) {

        return service.startPreparation(
            userId(authentication),
            ticketId
        );
    }

    @PostMapping("/tickets/{ticketId}/ready")
    public KitchenTicketMutationResponse ready(
        @PathVariable UUID ticketId,
        JwtAuthenticationToken authentication
    ) {

        return service.markReady(
            userId(authentication),
            ticketId
        );
    }

    private UUID userId(
        JwtAuthenticationToken authentication
    ) {

        try {

            return UUID.fromString(
                authentication
                    .getToken()
                    .getSubject()
            );

        } catch (
            IllegalArgumentException exception
        ) {

            throw new BadCredentialsException(
                "Invalid JWT subject."
            );
        }
    }
}
