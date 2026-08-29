package com.sup2i.food.pos.api;

import com.sup2i.food.order.api.dto.OrderResponse;
import com.sup2i.food.pos.api.dto.PosSaleQuoteResponse;
import com.sup2i.food.pos.api.dto.PosSaleRequest;
import com.sup2i.food.pos.service.PosSaleService;
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
    "/api/v1/pos/sales"
)
public class PosSaleController {

    private final PosSaleService service;

    public PosSaleController(
        PosSaleService service
    ) {
        this.service =
            service;
    }

    @PostMapping(
        "/quote"
    )
    @PreAuthorize(
        "hasAuthority('order.create')"
    )
    public PosSaleQuoteResponse quote(
        @Valid
        @RequestBody
        PosSaleRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.quote(
            userId(
                authentication
            ),
            request
        );
    }

    @PostMapping
    @ResponseStatus(
        HttpStatus.CREATED
    )
    @PreAuthorize(
        "hasAuthority('order.create') and hasAuthority('order.confirm')"
    )
    public OrderResponse create(
        @RequestHeader(
            name = "Idempotency-Key"
        )
        @NotBlank
        @Size(
            min = 8,
            max = 160
        )
        String idempotencyKey,
        @Valid
        @RequestBody
        PosSaleRequest request,
        JwtAuthenticationToken authentication
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

        try {

            return UUID.fromString(
                authentication
                    .getToken()
                    .getSubject()
            );

        } catch (
            NullPointerException
            | IllegalArgumentException exception
        ) {

            throw new BadCredentialsException(
                "Invalid JWT subject."
            );
        }
    }
}