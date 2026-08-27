package com.sup2i.food.catalog.api;

import com.sup2i.food.catalog.api.dto.CreateRecipeVersionRequest;
import com.sup2i.food.catalog.api.dto.RecipeResponse;
import com.sup2i.food.catalog.service.CatalogRecipeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Tag(name = "Catalog", description = "Categories, products, menus, recipes, and dietary metadata.")
@RestController
@RequestMapping(
    "/api/v1/admin/products"
)
public class AdminRecipeController {

    private final CatalogRecipeService service;

    public AdminRecipeController(
        CatalogRecipeService service
    ) {
        this.service = service;
    }

    @PostMapping(
        "/{productId}/recipes"
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public RecipeResponse createVersion(
        @PathVariable UUID productId,
        @Valid
        @RequestBody
        CreateRecipeVersionRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.createVersion(
            userId(authentication),
            productId,
            request
        );
    }

    @GetMapping(
        "/{productId}/recipes/current"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public RecipeResponse current(
        @PathVariable UUID productId,
        @RequestParam(
            required = false
        )
        UUID variantId,
        JwtAuthenticationToken authentication
    ) {

        return service.current(
            userId(authentication),
            productId,
            variantId
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