package com.sup2i.food.qr.api;

import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.exception.OrderNotFoundException;
import com.sup2i.food.order.repository.OrderRepository;
import com.sup2i.food.qr.api.dto.ScanResolveRequest;
import com.sup2i.food.qr.api.dto.ScanResolveResponse;
import com.sup2i.food.qr.domain.QrCredentialType;
import com.sup2i.food.qr.service.QrCredentialService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(
    name = "Scan",
    description = "Universal scan resolver: identifies a scanned QR credential and returns its live status."
)
@RestController
@RequestMapping("/api/v1/scans")
@PreAuthorize("isAuthenticated()")
public class ScanController {

    private final QrCredentialService qrCredentialService;
    private final OrderRepository orderRepository;

    public ScanController(
        QrCredentialService qrCredentialService,
        OrderRepository orderRepository
    ) {
        this.qrCredentialService =
            qrCredentialService;

        this.orderRepository =
            orderRepository;
    }

    @PostMapping("/resolve")
    public ScanResolveResponse resolve(
        @Valid
        @RequestBody
        ScanResolveRequest request
    ) {

        QrCredentialService.ResolvedCredential resolved =
            qrCredentialService.resolve(
                request.token()
            );

        if (
            resolved.credentialType()
                == QrCredentialType.ORDER
        ) {
            return resolveOrder(
                resolved.subjectId()
            );
        }

        return new ScanResolveResponse(
            resolved.credentialType()
                .name(),
            resolved.subjectId(),
            List.of(),
            Map.of()
        );
    }

    private ScanResolveResponse resolveOrder(
        UUID orderId
    ) {

        Order order =
            orderRepository
                .findById(orderId)
                .orElseThrow(() ->
                    new OrderNotFoundException(
                        "Order does not exist."
                    )
                );

        List<String> allowedActions =
            order.getStatus()
                == OrderStatus.AWAITING_PAYMENT
                    ? List.of("PAY")
                    : List.of();

        Map<String, Object> details =
            new LinkedHashMap<>();

        details.put(
            "orderNumber",
            order.getOrderNumber()
        );

        details.put(
            "orderStatus",
            order.getStatus()
                .name()
        );

        details.put(
            "total",
            order.getTotal()
        );

        details.put(
            "currency",
            order.getCurrency()
        );

        return new ScanResolveResponse(
            "ORDER",
            order.getId(),
            allowedActions,
            details
        );
    }
}
