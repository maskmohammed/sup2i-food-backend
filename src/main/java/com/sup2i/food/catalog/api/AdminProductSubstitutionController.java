package com.sup2i.food.catalog.api;

import com.sup2i.food.catalog.api.dto.ProductSubstitutionResponse;
import com.sup2i.food.catalog.api.dto.UpsertProductSubstitutionRequest;
import com.sup2i.food.catalog.service.CatalogSubstitutionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@Tag(name = "Catalog", description = "Categories, products, menus, recipes, and dietary metadata.")
@RestController
@RequestMapping(
    "/api/v1/admin/products"
)
public class AdminProductSubstitutionController {

    private final CatalogSubstitutionService service;

    public AdminProductSubstitutionController(
        CatalogSubstitutionService service
    ) {
        this.service = service;
    }

    @PutMapping(
        "/{productId}/substitutions/{substituteProductId}"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public ProductSubstitutionResponse upsert(
        @PathVariable UUID productId,
        @PathVariable UUID substituteProductId,
        @Valid
        @RequestBody
        UpsertProductSubstitutionRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.upsert(
            userId(authentication),
            productId,
            substituteProductId,
            request
        );
    }

    @GetMapping(
        "/{productId}/substitutions"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public List<ProductSubstitutionResponse>
        findAll(
            @PathVariable UUID productId,
            JwtAuthenticationToken authentication
        ) {

        return service.adminFindAll(
            userId(authentication),
            productId
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