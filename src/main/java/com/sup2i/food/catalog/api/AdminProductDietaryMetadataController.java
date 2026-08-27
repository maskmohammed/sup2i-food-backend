package com.sup2i.food.catalog.api;

import com.sup2i.food.catalog.api.dto.ProductDietaryMetadataResponse;
import com.sup2i.food.catalog.api.dto.UpsertProductDietaryMetadataRequest;
import com.sup2i.food.catalog.service.CatalogDietaryMetadataService;
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
public class AdminProductDietaryMetadataController {

    private final CatalogDietaryMetadataService service;

    public AdminProductDietaryMetadataController(
        CatalogDietaryMetadataService service
    ) {
        this.service = service;
    }

    @PutMapping(
        "/{productId}/dietary-metadata"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public ProductDietaryMetadataResponse replace(
        @PathVariable UUID productId,
        @Valid
        @RequestBody
        UpsertProductDietaryMetadataRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.replace(
            userId(authentication),
            productId,
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