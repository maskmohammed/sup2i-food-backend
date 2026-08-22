package com.sup2i.food.inventory.api;

import com.sup2i.food.inventory.api.dto.StockAlertMutationResponse;
import com.sup2i.food.inventory.api.dto.StockAlertReconcileResponse;
import com.sup2i.food.inventory.api.dto.StockAlertResponse;
import com.sup2i.food.inventory.domain.StockAlertStatus;
import com.sup2i.food.inventory.domain.StockAlertType;
import com.sup2i.food.inventory.service.InventoryAlertService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(
    "/api/v1/admin/inventory/alerts"
)
public class AdminInventoryAlertController {

    private final InventoryAlertService service;

    public AdminInventoryAlertController(
        InventoryAlertService service
    ) {
        this.service =
            service;
    }

    @PostMapping("/reconcile")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockAlertReconcileResponse reconcile(
        JwtAuthenticationToken authentication
    ) {

        return service.reconcile(
            userId(authentication)
        );
    }

    @GetMapping
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public List<StockAlertResponse> search(
        @RequestParam(required = false)
        StockAlertStatus status,

        @RequestParam(required = false)
        StockAlertType alertType,

        @RequestParam(required = false)
        UUID stockLocationId,

        @RequestParam(required = false)
        UUID stockItemId,

        JwtAuthenticationToken authentication
    ) {

        return service.search(
            userId(authentication),
            status,
            alertType,
            stockLocationId,
            stockItemId
        );
    }

    @GetMapping("/{alertId}")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockAlertResponse find(
        @PathVariable UUID alertId,
        JwtAuthenticationToken authentication
    ) {

        return service.find(
            userId(authentication),
            alertId
        );
    }

    @PostMapping("/{alertId}/acknowledge")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockAlertMutationResponse acknowledge(
        @PathVariable UUID alertId,
        JwtAuthenticationToken authentication
    ) {

        return service.acknowledge(
            userId(authentication),
            alertId
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