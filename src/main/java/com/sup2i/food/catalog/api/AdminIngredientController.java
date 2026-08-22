package com.sup2i.food.catalog.api;

import com.sup2i.food.catalog.api.dto.CreateIngredientRequest;
import com.sup2i.food.catalog.api.dto.IngredientResponse;
import com.sup2i.food.catalog.api.dto.ReplaceIngredientAllergensRequest;
import com.sup2i.food.catalog.api.dto.UpdateIngredientRequest;
import com.sup2i.food.catalog.service.CatalogIngredientService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(
    "/api/v1/admin/ingredients"
)
public class AdminIngredientController {

    private final CatalogIngredientService service;

    public AdminIngredientController(
        CatalogIngredientService service
    ) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public IngredientResponse create(
        @Valid
        @RequestBody
        CreateIngredientRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.create(
            userId(authentication),
            request
        );
    }

    @GetMapping
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public List<IngredientResponse> findAll(
        JwtAuthenticationToken authentication
    ) {

        return service.findAll(
            userId(authentication)
        );
    }

    @GetMapping("/{ingredientId}")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public IngredientResponse findOne(
        @PathVariable UUID ingredientId,
        JwtAuthenticationToken authentication
    ) {

        return service.findOne(
            userId(authentication),
            ingredientId
        );
    }

    @PutMapping(
        "/{ingredientId}"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public IngredientResponse update(
        @PathVariable UUID ingredientId,
        @Valid
        @RequestBody
        UpdateIngredientRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.update(
            userId(authentication),
            ingredientId,
            request
        );
    }
    @PutMapping(
        "/{ingredientId}/allergens"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public IngredientResponse replaceAllergens(
        @PathVariable UUID ingredientId,
        @Valid
        @RequestBody
        ReplaceIngredientAllergensRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.replaceAllergens(
            userId(authentication),
            ingredientId,
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