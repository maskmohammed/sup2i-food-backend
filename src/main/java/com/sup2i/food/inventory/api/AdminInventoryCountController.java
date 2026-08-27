package com.sup2i.food.inventory.api;

import com.sup2i.food.inventory.api.dto.CountInventoryItemRequest;
import com.sup2i.food.inventory.api.dto.CreateInventorySessionRequest;
import com.sup2i.food.inventory.api.dto.InventoryCountLineResponse;
import com.sup2i.food.inventory.api.dto.InventorySessionMutationResponse;
import com.sup2i.food.inventory.api.dto.InventorySessionResponse;
import com.sup2i.food.inventory.service.InventoryCountService;
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

import java.util.UUID;

@Tag(name = "Inventory", description = "Stock items, balances, movements, receipts, transfers, and alerts.")
@RestController
@RequestMapping(
    "/api/v1/admin/inventory/sessions"
)
public class AdminInventoryCountController {

    private final InventoryCountService service;

    public AdminInventoryCountController(
        InventoryCountService service
    ) {
        this.service = service;
    }

    @PutMapping("/{sessionId}")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public InventorySessionMutationResponse
        create(
            @PathVariable UUID sessionId,
            @Valid
            @RequestBody
            CreateInventorySessionRequest request,
            JwtAuthenticationToken authentication
        ) {

        return service.createSession(
            userId(authentication),
            sessionId,
            request
        );
    }

    @PutMapping(
        "/{sessionId}/items/{stockItemId}"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public InventoryCountLineResponse count(
        @PathVariable UUID sessionId,
        @PathVariable UUID stockItemId,
        @Valid
        @RequestBody
        CountInventoryItemRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.count(
            userId(authentication),
            sessionId,
            stockItemId,
            request
        );
    }

    @PostMapping(
        "/{sessionId}/complete"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public InventorySessionMutationResponse
        complete(
            @PathVariable UUID sessionId,
            JwtAuthenticationToken authentication
        ) {

        return service.complete(
            userId(authentication),
            sessionId
        );
    }

    @PostMapping(
        "/{sessionId}/apply"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public InventorySessionMutationResponse
        apply(
            @PathVariable UUID sessionId,
            JwtAuthenticationToken authentication
        ) {

        return service.apply(
            userId(authentication),
            sessionId
        );
    }

    @PostMapping(
        "/{sessionId}/cancel"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public InventorySessionMutationResponse
        cancel(
            @PathVariable UUID sessionId,
            JwtAuthenticationToken authentication
        ) {

        return service.cancel(
            userId(authentication),
            sessionId
        );
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public InventorySessionResponse find(
        @PathVariable UUID sessionId,
        JwtAuthenticationToken authentication
    ) {

        return service.findSession(
            userId(authentication),
            sessionId
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