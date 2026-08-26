package com.sup2i.food.payment.api;

import com.sup2i.food.payment.api.dto.CreatePaymentRequest;
import com.sup2i.food.payment.api.dto.PaymentMethodRequest;
import com.sup2i.food.payment.api.dto.PaymentResponse;
import com.sup2i.food.payment.domain.PaymentMethod;
import com.sup2i.food.payment.service.PaymentCaptureCommand;
import com.sup2i.food.payment.service.PaymentCaptureResult;
import com.sup2i.food.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(
        PaymentService service
    ) {
        this.service =
            service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('payment.collect')"
    )
    public PaymentResponse create(
        @RequestHeader(
            name = "Idempotency-Key",
            required = false
        )
        String idempotencyKey,
        @Valid
        @RequestBody
        CreatePaymentRequest request,
        JwtAuthenticationToken authentication
    ) {

        PaymentCaptureCommand command =
            new PaymentCaptureCommand(
                toDomainMethod(
                    request.method()
                ),
                idempotencyKey,
                request.amount(),
                request.externalReference(),
                request.posSessionId()
            );

        PaymentCaptureResult result =
            service.capture(
                userId(authentication),
                request.orderId(),
                command
            );

        return toResponse(result);
    }

    private PaymentMethod toDomainMethod(
        PaymentMethodRequest method
    ) {

        return switch (method) {

            case CASH ->
                PaymentMethod.CASH;

            case CARD_TPE ->
                PaymentMethod.CARD_TPE;
        };
    }

    private PaymentResponse toResponse(
        PaymentCaptureResult result
    ) {

        return new PaymentResponse(
            result.paymentId(),
            result.orderId(),
            result.method().name(),
            result.status().name(),
            result.amount(),
            result.currency(),
            result.paidAt()
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