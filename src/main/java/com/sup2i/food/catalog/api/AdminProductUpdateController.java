package com.sup2i.food.catalog.api;

import com.sup2i.food.catalog.api.dto.AdminUpdateProductRequest;
import com.sup2i.food.catalog.api.dto.ProductResponse;
import com.sup2i.food.catalog.service.AdminProductUpdateService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductUpdateController {

    private final AdminProductUpdateService service;

    public AdminProductUpdateController(
        AdminProductUpdateService service
    ) {
        this.service =
            service;
    }

    @PatchMapping("/{productId}")
    @PreAuthorize(
        "hasAuthority('product.write')"
    )
    public ProductResponse update(
        @PathVariable
        UUID productId,

        @Valid
        @RequestBody
        AdminUpdateProductRequest request,

        JwtAuthenticationToken authentication
    ) {

        return service.update(
            userId(
                authentication
            ),
            productId,
            request,
            hasAuthority(
                authentication,
                "price.update"
            )
        );
    }

    private boolean hasAuthority(
        Authentication authentication,
        String authority
    ) {

        return authentication
            .getAuthorities()
            .stream()
            .anyMatch(granted ->
                authority.equals(
                    granted.getAuthority()
                )
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
            IllegalArgumentException
            | NullPointerException exception
        ) {

            throw new BadCredentialsException(
                "Invalid authenticated user identifier.",
                exception
            );
        }
    }
}