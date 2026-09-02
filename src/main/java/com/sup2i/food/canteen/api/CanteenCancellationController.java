package com.sup2i.food.canteen.api;

import com.sup2i.food.canteen.api.dto.CanteenReservationResponse;
import com.sup2i.food.canteen.service.CanteenCancellationService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping(
    "/api/v1/canteen/reservations"
)
public class CanteenCancellationController {

    private final CanteenCancellationService service;

    public CanteenCancellationController(
        CanteenCancellationService service
    ) {
        this.service =
            service;
    }

    @PostMapping(
        "/{reservationId}/cancel"
    )
    @PreAuthorize(
        "hasAuthority('canteen.cancel_reservation')"
    )
    public CanteenReservationResponse cancel(
        JwtAuthenticationToken authentication,

        @PathVariable
        UUID reservationId
    ) {
        return service.cancel(
            actorId(
                authentication
            ),
            reservationId
        );
    }

    private UUID actorId(
        JwtAuthenticationToken authentication
    ) {
        if (
            authentication == null
            || authentication.getToken() == null
            || authentication.getToken().getSubject() == null
        ) {
            throw new BadCredentialsException(
                "Authenticated user identifier is missing."
            );
        }

        try {
            return UUID.fromString(
                authentication
                    .getToken()
                    .getSubject()
            );
        }
        catch (IllegalArgumentException exception) {
            throw new BadCredentialsException(
                "Authenticated user identifier is invalid.",
                exception
            );
        }
    }
}