package com.sup2i.food.order.api;

import com.sup2i.food.order.api.dto.CreateOrderRequest;
import com.sup2i.food.order.api.dto.OrderResponse;
import com.sup2i.food.order.service.MobileOrderCreationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping(
    "/api/v1/orders"
)
public class MobileOrderCreationController {

    private final MobileOrderCreationService service;

    public MobileOrderCreationController(
        MobileOrderCreationService service
    ) {
        this.service =
            service;
    }

    @PostMapping
    @ResponseStatus(
        HttpStatus.CREATED
    )
    @PreAuthorize(
        "isAuthenticated()"
    )
    public OrderResponse create(
        JwtAuthenticationToken authentication,

        @RequestHeader(
            name = "Idempotency-Key",
            required = true
        )
        @NotBlank
        @Size(
            min = 8,
            max = 160
        )
        String idempotencyKey,

        @Valid
        @RequestBody
        CreateOrderRequest request
    ) {
        return service.create(
            userId(
                authentication
            ),
            idempotencyKey,
            request
        );
    }

    private UUID userId(
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
                "Invalid JWT subject.",
                exception
            );
        }
    }
}