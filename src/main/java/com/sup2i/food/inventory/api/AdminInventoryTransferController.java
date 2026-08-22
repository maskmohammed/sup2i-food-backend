package com.sup2i.food.inventory.api;

import com.sup2i.food.inventory.api.dto.CreateStockTransferRequest;
import com.sup2i.food.inventory.api.dto.StockTransferMutationResponse;
import com.sup2i.food.inventory.api.dto.StockTransferResponse;
import com.sup2i.food.inventory.service.InventoryTransferService;
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

import java.util.UUID;

@RestController
@RequestMapping(
    "/api/v1/admin/inventory/transfers"
)
public class AdminInventoryTransferController {

    private final InventoryTransferService service;

    public AdminInventoryTransferController(
        InventoryTransferService service
    ) {
        this.service =
            service;
    }

    @PutMapping("/{transferId}")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockTransferMutationResponse upsertDraft(
        @PathVariable UUID transferId,
        @Valid
        @RequestBody
        CreateStockTransferRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.upsertDraft(
            userId(authentication),
            transferId,
            request
        );
    }

    @PostMapping("/{transferId}/approve")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockTransferMutationResponse approve(
        @PathVariable UUID transferId,
        JwtAuthenticationToken authentication
    ) {

        return service.approve(
            userId(authentication),
            transferId
        );
    }

    @PostMapping("/{transferId}/dispatch")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockTransferMutationResponse dispatch(
        @PathVariable UUID transferId,
        JwtAuthenticationToken authentication
    ) {

        return service.dispatch(
            userId(authentication),
            transferId
        );
    }

    @PostMapping("/{transferId}/receive")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockTransferMutationResponse receive(
        @PathVariable UUID transferId,
        JwtAuthenticationToken authentication
    ) {

        return service.receive(
            userId(authentication),
            transferId
        );
    }

    @PostMapping("/{transferId}/cancel")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockTransferMutationResponse cancel(
        @PathVariable UUID transferId,
        JwtAuthenticationToken authentication
    ) {

        return service.cancel(
            userId(authentication),
            transferId
        );
    }

    @GetMapping("/{transferId}")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockTransferResponse find(
        @PathVariable UUID transferId,
        JwtAuthenticationToken authentication
    ) {

        return service.find(
            userId(authentication),
            transferId
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