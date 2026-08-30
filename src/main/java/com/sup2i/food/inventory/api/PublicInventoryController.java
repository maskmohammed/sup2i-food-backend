package com.sup2i.food.inventory.api;

import com.sup2i.food.inventory.api.contract.InventoryAdjustmentRequest;
import com.sup2i.food.inventory.api.contract.InventoryItemResponse;
import com.sup2i.food.inventory.api.contract.InventoryMovementResponse;
import com.sup2i.food.inventory.service.PublicInventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/inventory")
public class PublicInventoryController {

    private final PublicInventoryService service;

    public PublicInventoryController(
        PublicInventoryService service
    ) {
        this.service = service;
    }

    @GetMapping("/items")
    @PreAuthorize(
        "hasAuthority('inventory.read')"
    )
    public List<InventoryItemResponse> listItems(
        @RequestParam(
            name = "stockLocationId"
        )
        UUID stockLocationId,

        @RequestParam(
            name = "lowStockOnly",
            required = false,
            defaultValue = "false"
        )
        boolean lowStockOnly,

        JwtAuthenticationToken authentication
    ) {

        return service.listItems(
            userId(authentication),
            stockLocationId,
            lowStockOnly
        );
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('inventory.adjust')"
    )
    public InventoryMovementResponse adjust(
        @NotBlank
        @Size(
            min = 8,
            max = 160
        )
        @RequestHeader(
            name = "Idempotency-Key"
        )
        String idempotencyKey,

        @Valid
        @RequestBody
        InventoryAdjustmentRequest request,

        JwtAuthenticationToken authentication
    ) {

        return service.adjust(
            userId(authentication),
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
            IllegalArgumentException
            | NullPointerException exception
        ) {

            throw new BadCredentialsException(
                "Invalid JWT subject.",
                exception
            );
        }
    }
}