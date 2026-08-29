package com.sup2i.food.pos.api;

import com.sup2i.food.pos.api.dto.PosCheckoutResponse;
import com.sup2i.food.pos.api.dto.PosPaymentRequest;
import com.sup2i.food.pos.service.PosCheckoutService;
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
    "/api/v1/pos/payments"
)
public class PosPaymentController {

    private final PosCheckoutService service;

    public PosPaymentController(
        PosCheckoutService service
    ) {
        this.service =
            service;
    }

    @PostMapping
    @ResponseStatus(
        HttpStatus.CREATED
    )
    @PreAuthorize(
        "hasAuthority('payment.collect')"
    )
    public PosCheckoutResponse checkout(
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
        PosPaymentRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.checkout(
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