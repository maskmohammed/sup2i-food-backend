package com.sup2i.food.catalog.api;

import com.sup2i.food.catalog.api.dto.CreateProductOptionComponentRequest;
import com.sup2i.food.catalog.api.dto.ProductOptionComponentResponse;
import com.sup2i.food.catalog.service.CatalogOptionComponentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@Tag(name = "Catalog", description = "Categories, products, menus, recipes, and dietary metadata.")
@RestController
@RequestMapping(
    "/api/v1/admin/products"
)
public class AdminProductOptionComponentController {

    private final CatalogOptionComponentService service;

    public AdminProductOptionComponentController(
        CatalogOptionComponentService service
    ) {
        this.service = service;
    }

    @PostMapping(
        "/{productId}/options/{optionId}/components"
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public ProductOptionComponentResponse create(
        @PathVariable UUID productId,
        @PathVariable UUID optionId,
        @Valid
        @RequestBody
        CreateProductOptionComponentRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.create(
            userId(authentication),
            productId,
            optionId,
            request
        );
    }

    @GetMapping(
        "/{productId}/options/{optionId}/components"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public List<ProductOptionComponentResponse>
        findAll(
            @PathVariable UUID productId,
            @PathVariable UUID optionId,
            JwtAuthenticationToken authentication
        ) {

        return service.findAll(
            userId(authentication),
            productId,
            optionId
        );
    }

    @DeleteMapping(
        "/{productId}/options/{optionId}/components/{componentId}"
    )
    @ResponseStatus(
        HttpStatus.NO_CONTENT
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public void delete(
        @PathVariable UUID productId,
        @PathVariable UUID optionId,
        @PathVariable UUID componentId,
        JwtAuthenticationToken authentication
    ) {

        service.delete(
            userId(authentication),
            productId,
            optionId,
            componentId
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