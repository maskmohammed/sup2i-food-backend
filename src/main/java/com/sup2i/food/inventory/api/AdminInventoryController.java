package com.sup2i.food.inventory.api;

import com.sup2i.food.inventory.api.dto.ApplyInventoryAdjustmentRequest;
import com.sup2i.food.inventory.api.dto.CreateStockItemRequest;
import com.sup2i.food.inventory.api.dto.CreateStockLocationRequest;
import com.sup2i.food.inventory.api.dto.InventoryAdjustmentResponse;
import com.sup2i.food.inventory.api.dto.StockBalanceResponse;
import com.sup2i.food.inventory.api.dto.StockItemResponse;
import com.sup2i.food.inventory.api.dto.StockLocationResponse;
import com.sup2i.food.inventory.api.dto.UpdateStockItemRequest;
import com.sup2i.food.inventory.service.InventoryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(
    "/api/v1/admin/inventory"
)
public class AdminInventoryController {

    private final InventoryService service;

    public AdminInventoryController(
        InventoryService service
    ) {
        this.service = service;
    }

    @PostMapping("/stock-locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockLocationResponse
        createStockLocation(
            @Valid
            @RequestBody
            CreateStockLocationRequest request,
            JwtAuthenticationToken authentication
        ) {

        return service.createStockLocation(
            userId(authentication),
            request
        );
    }

    @GetMapping("/stock-locations")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public List<StockLocationResponse>
        findStockLocations(
            JwtAuthenticationToken authentication
        ) {

        return service.findStockLocations(
            userId(authentication)
        );
    }

    @PostMapping("/stock-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockItemResponse createStockItem(
        @Valid
        @RequestBody
        CreateStockItemRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.createStockItem(
            userId(authentication),
            request
        );
    }

    @PutMapping(
        "/stock-items/{stockItemId}"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockItemResponse updateStockItem(
        @PathVariable UUID stockItemId,
        @Valid
        @RequestBody
        UpdateStockItemRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.updateStockItem(
            userId(authentication),
            stockItemId,
            request
        );
    }

    @GetMapping("/stock-items")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public List<StockItemResponse>
        findStockItems(
            JwtAuthenticationToken authentication
        ) {

        return service.findStockItems(
            userId(authentication)
        );
    }

    @GetMapping("/balances")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public List<StockBalanceResponse>
        findBalances(
            @RequestParam(required = false)
            UUID stockLocationId,
            JwtAuthenticationToken authentication
        ) {

        return service.findBalances(
            userId(authentication),
            stockLocationId
        );
    }

    @PostMapping("/adjustments")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public InventoryAdjustmentResponse adjust(
        @Valid
        @RequestBody
        ApplyInventoryAdjustmentRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.adjust(
            userId(authentication),
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
            IllegalArgumentException exception
        ) {
            throw new BadCredentialsException(
                "Invalid JWT subject."
            );
        }
    }
}