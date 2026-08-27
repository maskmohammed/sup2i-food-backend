package com.sup2i.food.catalog.api;

import com.sup2i.food.catalog.api.dto.ProductLocationSettingResponse;
import com.sup2i.food.catalog.api.dto.UpsertProductLocationSettingRequest;
import com.sup2i.food.catalog.service.CatalogLocationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Tag(name = "Catalog", description = "Categories, products, menus, recipes, and dietary metadata.")
@RestController
@RequestMapping(
    "/api/v1/admin/products"
)
public class AdminProductLocationController {

    private final CatalogLocationService service;

    public AdminProductLocationController(
        CatalogLocationService service
    ) {
        this.service = service;
    }

    @PutMapping(
        "/{productId}/locations/{locationId}"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public ProductLocationSettingResponse upsert(
        @PathVariable UUID productId,
        @PathVariable UUID locationId,
        @Valid
        @RequestBody
        UpsertProductLocationSettingRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.upsert(
            userId(authentication),
            productId,
            locationId,
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