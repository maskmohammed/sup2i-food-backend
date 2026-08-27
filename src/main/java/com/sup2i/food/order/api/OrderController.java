package com.sup2i.food.order.api;

import com.sup2i.food.order.api.dto.OrderMutationResponse;
import com.sup2i.food.order.api.dto.OrderResponse;
import com.sup2i.food.order.api.dto.OrderStatusHistoryResponse;
import com.sup2i.food.order.api.dto.UpsertOrderRequest;
import com.sup2i.food.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@Tag(name = "Orders", description = "Mobile order lifecycle: draft, submission, payment, and history.")
@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("isAuthenticated()")
public class OrderController {

    private final OrderService service;

    public OrderController(
        OrderService service
    ) {
        this.service =
            service;
    }

    @PutMapping("/{orderId}")
    public OrderMutationResponse upsertDraft(
        @PathVariable UUID orderId,
        @Valid
        @RequestBody
        UpsertOrderRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.upsertDraft(
            userId(authentication),
            orderId,
            request
        );
    }

    @PostMapping("/{orderId}/submit")
    public OrderMutationResponse submit(
        @PathVariable UUID orderId,
        JwtAuthenticationToken authentication
    ) {

        return service.submit(
            userId(authentication),
            orderId
        );
    }

    @PostMapping("/{orderId}/begin-payment")
    public OrderMutationResponse beginPayment(
        @PathVariable UUID orderId,
        JwtAuthenticationToken authentication
    ) {

        return service.beginPayment(
            userId(authentication),
            orderId
        );
    }

    @PostMapping("/{orderId}/pay")
    public OrderMutationResponse pay(
        @PathVariable UUID orderId,
        JwtAuthenticationToken authentication
    ) {

        return service.pay(
            userId(authentication),
            orderId
        );
    }

    @PostMapping("/{orderId}/cancel")
    public OrderMutationResponse cancel(
        @PathVariable UUID orderId,
        JwtAuthenticationToken authentication
    ) {

        return service.cancel(
            userId(authentication),
            orderId
        );
    }

    @PostMapping("/{orderId}/expire")
    public OrderMutationResponse expire(
        @PathVariable UUID orderId,
        JwtAuthenticationToken authentication
    ) {

        return service.expire(
            userId(authentication),
            orderId
        );
    }

    @GetMapping("/{orderId}")
    public OrderResponse find(
        @PathVariable UUID orderId,
        JwtAuthenticationToken authentication
    ) {

        return service.find(
            userId(authentication),
            orderId
        );
    }

    @GetMapping("/{orderId}/history")
    public List<OrderStatusHistoryResponse> history(
        @PathVariable UUID orderId,
        JwtAuthenticationToken authentication
    ) {

        return service.history(
            userId(authentication),
            orderId
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