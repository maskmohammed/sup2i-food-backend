package com.sup2i.food.order.api;

import com.sup2i.food.order.api.dto.PagedOrdersResponse;
import com.sup2i.food.order.api.dto.ReadyOrderResponse;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.service.OrderService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class OrderContractController {

    private final OrderService orderService;

    public OrderContractController(
        OrderService orderService
    ) {
        this.orderService =
            orderService;
    }

    @GetMapping("/orders/ready")
    public List<ReadyOrderResponse> ready(
        @RequestParam UUID locationId
    ) {

        return orderService.ready(
            locationId
        );
    }

    @GetMapping("/me/orders")
    @PreAuthorize("isAuthenticated()")
    public PagedOrdersResponse mine(
        JwtAuthenticationToken authentication,

        @RequestParam(
            defaultValue = "0"
        )
        int page,

        @RequestParam(
            defaultValue = "20"
        )
        int size,

        @RequestParam(
            required = false
        )
        OrderStatus status
    ) {

        return orderService.mine(
            userId(
                authentication
            ),
            page,
            size,
            status
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
        }
        catch (
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