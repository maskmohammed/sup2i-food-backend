package com.sup2i.food.catalog.api;

import com.sup2i.food.catalog.api.dto.CategoryResponse;
import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.catalog.api.dto.ProductResponse;
import com.sup2i.food.catalog.service.CatalogQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Tag(name = "Catalog", description = "Categories, products, menus, recipes, and dietary metadata.")
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogQueryService
        catalogQueryService;

    public CatalogController(
        CatalogQueryService catalogQueryService
    ) {
        this.catalogQueryService =
            catalogQueryService;
    }

    @GetMapping("/categories")
    @PreAuthorize(
        "hasAuthority('catalog.read')"
    )
    public PageResponse<CategoryResponse>
        categories(
            JwtAuthenticationToken authentication,

            @RequestParam(
                defaultValue = "0"
            )
            int page,

            @RequestParam(
                defaultValue = "20"
            )
            int size
        ) {

        return catalogQueryService
            .categories(
                userId(authentication),
                page,
                size
            );
    }

    @GetMapping("/products")
    @PreAuthorize(
        "hasAuthority('catalog.read')"
    )
    public PageResponse<ProductResponse>
        products(
            JwtAuthenticationToken authentication,

            @RequestParam(
                required = false
            )
            UUID categoryId,

            @RequestParam(
                defaultValue = "0"
            )
            int page,

            @RequestParam(
                defaultValue = "20"
            )
            int size
        ) {

        return catalogQueryService
            .products(
                userId(authentication),
                categoryId,
                page,
                size
            );
    }

    @GetMapping("/products/{id}")
    @PreAuthorize(
        "hasAuthority('catalog.read')"
    )
    public ProductResponse product(
        @PathVariable UUID id,
        JwtAuthenticationToken authentication
    ) {

        return catalogQueryService
            .product(
                userId(authentication),
                id
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
        }
        catch (
            IllegalArgumentException exception
        ) {
            throw new BadCredentialsException(
                "Invalid JWT subject."
            );
        }
    }
}