package com.sup2i.food.inventory.api;

import com.sup2i.food.inventory.api.dto.ReceiveStockRequest;
import com.sup2i.food.inventory.api.dto.StockLotResponse;
import com.sup2i.food.inventory.api.dto.StockReceiptResponse;
import com.sup2i.food.inventory.api.dto.UpsertStockReceiptResponse;
import com.sup2i.food.inventory.service.InventoryReceiptService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(
    "/api/v1/admin/inventory"
)
public class AdminInventoryReceiptController {

    private final InventoryReceiptService service;

    public AdminInventoryReceiptController(
        InventoryReceiptService service
    ) {
        this.service = service;
    }

    @PutMapping(
        "/receipts/{receiptId}"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public UpsertStockReceiptResponse receive(
        @PathVariable UUID receiptId,
        @Valid
        @RequestBody
        ReceiveStockRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.receive(
            userId(authentication),
            receiptId,
            request
        );
    }

    @GetMapping(
        "/receipts/{receiptId}"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockReceiptResponse findReceipt(
        @PathVariable UUID receiptId,
        JwtAuthenticationToken authentication
    ) {

        return service.findReceipt(
            userId(authentication),
            receiptId
        );
    }

    @GetMapping("/lots")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public List<StockLotResponse> findLots(
        @RequestParam(required = false)
        UUID stockLocationId,

        @RequestParam(required = false)
        UUID stockItemId,

        @RequestParam(
            defaultValue = "true"
        )
        boolean remainingOnly,

        JwtAuthenticationToken authentication
    ) {

        return service.findLots(
            userId(authentication),
            stockLocationId,
            stockItemId,
            remainingOnly
        );
    }

    @GetMapping("/lots/{lotId}")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public StockLotResponse findLot(
        @PathVariable UUID lotId,
        JwtAuthenticationToken authentication
    ) {

        return service.findLot(
            userId(authentication),
            lotId
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