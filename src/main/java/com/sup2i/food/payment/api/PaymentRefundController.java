package com.sup2i.food.payment.api;

import com.sup2i.food.payment.api.dto.RefundRequest;
import com.sup2i.food.payment.api.dto.RefundResponse;
import com.sup2i.food.payment.service.PaymentRefundService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
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
    "/api/v1/payments"
)
public class PaymentRefundController {

    private final PaymentRefundService service;

    public PaymentRefundController(
        PaymentRefundService service
    ) {
        this.service =
            service;
    }

    @PostMapping(
        "/{paymentId}/refunds"
    )
    @ResponseStatus(
        HttpStatus.CREATED
    )
    @PreAuthorize(
        "hasAuthority('payment.refund')"
    )
    public RefundResponse refund(
        JwtAuthenticationToken authentication,

        @PathVariable
        UUID paymentId,

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
        RefundRequest request
    ) {
        return service.refund(
            userId(
                authentication
            ),
            paymentId,
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