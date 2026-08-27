package com.sup2i.food.catalog.api;

import com.sup2i.food.catalog.api.dto.CategoryResponse;
import com.sup2i.food.catalog.api.dto.CreateCategoryRequest;
import com.sup2i.food.catalog.api.dto.CreateProductRequest;
import com.sup2i.food.catalog.api.dto.ProductResponse;
import com.sup2i.food.catalog.service.CatalogAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Tag(name = "Catalog", description = "Categories, products, menus, recipes, and dietary metadata.")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {

    private final CatalogAdminService
        catalogAdminService;

    public AdminCatalogController(
        CatalogAdminService catalogAdminService
    ) {
        this.catalogAdminService =
            catalogAdminService;
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('category.write')"
    )
    public CategoryResponse createCategory(
        @Valid
        @RequestBody
        CreateCategoryRequest request,

        JwtAuthenticationToken authentication
    ) {

        return catalogAdminService
            .createCategory(
                userId(authentication),
                request
            );
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public ProductResponse createProduct(
        @Valid
        @RequestBody
        CreateProductRequest request,

        JwtAuthenticationToken authentication
    ) {

        return catalogAdminService
            .createProduct(
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