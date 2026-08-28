package com.sup2i.food.purchase.api;

import com.sup2i.food.purchase.api.dto.CreatePurchaseOrderRequest;
import com.sup2i.food.purchase.api.dto.PurchaseOrderHistoryElement;
import com.sup2i.food.purchase.api.dto.PurchaseOrderResponse;
import com.sup2i.food.purchase.api.dto.ReceivePurchaseOrderRequest;
import com.sup2i.food.purchase.api.dto.UpdatePurchaseOrderRequest;
import com.sup2i.food.purchase.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Purchase orders", description = "Purchase order workflow and self-contained stock receipt.")
@RestController
@RequestMapping(
    "/api/v1/admin/purchase-orders"
)
public class AdminPurchaseOrderController {

    private final PurchaseOrderService service;

    public AdminPurchaseOrderController(
        PurchaseOrderService service
    ) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('purchase.write')"
    )
    public PurchaseOrderResponse create(
        @Valid
        @RequestBody
        CreatePurchaseOrderRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.create(
            userId(authentication),
            request
        );
    }

    @GetMapping
    @PreAuthorize(
        "hasAuthority('purchase.read')"
    )
    public List<PurchaseOrderResponse> findAll(
        JwtAuthenticationToken authentication
    ) {
        return service.findAll(
            userId(authentication)
        );
    }

    @GetMapping("/{purchaseOrderId}")
    @PreAuthorize(
        "hasAuthority('purchase.read')"
    )
    public PurchaseOrderResponse find(
        @PathVariable UUID purchaseOrderId,
        JwtAuthenticationToken authentication
    ) {
        return service.find(
            userId(authentication),
            purchaseOrderId
        );
    }

    @PutMapping("/{purchaseOrderId}")
    @PreAuthorize(
        "hasAuthority('purchase.write')"
    )
    public PurchaseOrderResponse update(
        @PathVariable UUID purchaseOrderId,
        @Valid
        @RequestBody
        UpdatePurchaseOrderRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.update(
            userId(authentication),
            purchaseOrderId,
            request
        );
    }

    @PostMapping("/{purchaseOrderId}/send")
    @PreAuthorize(
        "hasAuthority('purchase.write')"
    )
    public PurchaseOrderResponse send(
        @PathVariable UUID purchaseOrderId,
        JwtAuthenticationToken authentication
    ) {
        return service.send(
            userId(authentication),
            purchaseOrderId
        );
    }

    @PostMapping("/{purchaseOrderId}/confirm")
    @PreAuthorize(
        "hasAuthority('purchase.write')"
    )
    public PurchaseOrderResponse confirm(
        @PathVariable UUID purchaseOrderId,
        JwtAuthenticationToken authentication
    ) {
        return service.confirm(
            userId(authentication),
            purchaseOrderId
        );
    }

    @PostMapping("/{purchaseOrderId}/receive")
    @PreAuthorize(
        "hasAuthority('purchase.write')"
    )
    public PurchaseOrderResponse receive(
        @PathVariable UUID purchaseOrderId,
        @Valid
        @RequestBody
        ReceivePurchaseOrderRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.receive(
            userId(authentication),
            purchaseOrderId,
            request
        );
    }

    @PostMapping("/{purchaseOrderId}/cancel")
    @PreAuthorize(
        "hasAuthority('purchase.write')"
    )
    public PurchaseOrderResponse cancel(
        @PathVariable UUID purchaseOrderId,
        JwtAuthenticationToken authentication
    ) {
        return service.cancel(
            userId(authentication),
            purchaseOrderId
        );
    }

    @GetMapping("/{purchaseOrderId}/history")
    @PreAuthorize(
        "hasAuthority('purchase.read')"
    )
    public List<PurchaseOrderHistoryElement> history(
        @PathVariable UUID purchaseOrderId,
        JwtAuthenticationToken authentication
    ) {
        return service.history(
            userId(authentication),
            purchaseOrderId
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