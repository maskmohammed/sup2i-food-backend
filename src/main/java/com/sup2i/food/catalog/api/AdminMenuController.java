package com.sup2i.food.catalog.api;

import com.sup2i.food.catalog.api.dto.CreateMenuItemRequest;
import com.sup2i.food.catalog.api.dto.CreateMenuSectionRequest;
import com.sup2i.food.catalog.api.dto.MenuItemResponse;
import com.sup2i.food.catalog.api.dto.MenuResponse;
import com.sup2i.food.catalog.api.dto.MenuSectionResponse;
import com.sup2i.food.catalog.api.dto.UpsertMenuRequest;
import com.sup2i.food.catalog.service.CatalogMenuService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
    "/api/v1/admin/products"
)
public class AdminMenuController {

    private final CatalogMenuService service;

    public AdminMenuController(
        CatalogMenuService service
    ) {
        this.service = service;
    }

    @PutMapping(
        "/{productId}/menu"
    )
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public MenuResponse upsertMenu(
        @PathVariable UUID productId,
        @Valid
        @RequestBody
        UpsertMenuRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.upsertMenu(
            userId(authentication),
            productId,
            request
        );
    }

    @PostMapping(
        "/{productId}/menu/sections"
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public MenuSectionResponse createSection(
        @PathVariable UUID productId,
        @Valid
        @RequestBody
        CreateMenuSectionRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.createSection(
            userId(authentication),
            productId,
            request
        );
    }

    @PostMapping(
        "/{productId}/menu/sections/{sectionId}/items"
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public MenuItemResponse createItem(
        @PathVariable UUID productId,
        @PathVariable UUID sectionId,
        @Valid
        @RequestBody
        CreateMenuItemRequest request,
        JwtAuthenticationToken authentication
    ) {

        return service.createItem(
            userId(authentication),
            productId,
            sectionId,
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