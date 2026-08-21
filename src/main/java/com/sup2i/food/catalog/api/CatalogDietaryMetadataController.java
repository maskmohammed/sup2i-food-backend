package com.sup2i.food.catalog.api;

import com.sup2i.food.catalog.api.dto.DietaryReferenceResponse;
import com.sup2i.food.catalog.api.dto.ProductDietaryMetadataResponse;
import com.sup2i.food.catalog.service.CatalogDietaryMetadataService;
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
    "/api/v1/catalog"
)
public class CatalogDietaryMetadataController {

    private final CatalogDietaryMetadataService service;

    public CatalogDietaryMetadataController(
        CatalogDietaryMetadataService service
    ) {
        this.service = service;
    }

    @GetMapping(
        "/referentials/allergens"
    )
    @PreAuthorize(
        "hasAuthority('catalog.read')"
    )
    public List<DietaryReferenceResponse> allergens(
        JwtAuthenticationToken authentication
    ) {

        return service.allergens(
            userId(authentication)
        );
    }

    @GetMapping(
        "/referentials/dietary-tags"
    )
    @PreAuthorize(
        "hasAuthority('catalog.read')"
    )
    public List<DietaryReferenceResponse> dietaryTags(
        JwtAuthenticationToken authentication
    ) {

        return service.dietaryTags(
            userId(authentication)
        );
    }

    @GetMapping(
        "/products/{productId}/dietary-metadata"
    )
    @PreAuthorize(
        "hasAuthority('catalog.read')"
    )
    public ProductDietaryMetadataResponse metadata(
        @PathVariable UUID productId,
        JwtAuthenticationToken authentication
    ) {

        return service.metadata(
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