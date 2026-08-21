package com.sup2i.food.catalog.api;

import com.sup2i.food.catalog.api.dto.ProductLocationSettingResponse;
import com.sup2i.food.catalog.service.CatalogLocationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(
    "/api/v1/catalog/products"
)
public class CatalogProductLocationController {

    private final CatalogLocationService service;

    public CatalogProductLocationController(
        CatalogLocationService service
    ) {
        this.service = service;
    }

    @GetMapping(
        "/{productId}/locations"
    )
    @PreAuthorize(
        "hasAuthority('catalog.read')"
    )
    public List<ProductLocationSettingResponse>
        locations(
            @PathVariable UUID productId,
            JwtAuthenticationToken authentication
        ) {

        return service.findAll(
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