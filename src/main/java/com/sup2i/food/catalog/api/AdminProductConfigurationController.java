package com.sup2i.food.catalog.api;

import com.sup2i.food.catalog.api.dto.CreateProductBarcodeRequest;
import com.sup2i.food.catalog.api.dto.CreateProductOptionGroupRequest;
import com.sup2i.food.catalog.api.dto.CreateProductOptionRequest;
import com.sup2i.food.catalog.api.dto.CreateProductVariantRequest;
import com.sup2i.food.catalog.api.dto.ProductBarcodeResponse;
import com.sup2i.food.catalog.api.dto.ProductOptionGroupResponse;
import com.sup2i.food.catalog.api.dto.ProductOptionResponse;
import com.sup2i.food.catalog.api.dto.ProductVariantResponse;
import com.sup2i.food.catalog.service.CatalogAdvancedAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Tag(name = "Catalog", description = "Categories, products, menus, recipes, and dietary metadata.")
@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductConfigurationController {

    private final CatalogAdvancedAdminService service;

    public AdminProductConfigurationController(
        CatalogAdvancedAdminService service
    ) {
        this.service = service;
    }

    @PostMapping("/{productId}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('product.write')")
    public ProductVariantResponse createVariant(
        @PathVariable UUID productId,
        @Valid @RequestBody
        CreateProductVariantRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.createVariant(
            userId(authentication),
            productId,
            request
        );
    }

    @PostMapping("/{productId}/barcodes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('product.write')")
    public ProductBarcodeResponse createBarcode(
        @PathVariable UUID productId,
        @Valid @RequestBody
        CreateProductBarcodeRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.createBarcode(
            userId(authentication),
            productId,
            request
        );
    }

    @PostMapping("/{productId}/option-groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('product.write')")
    public ProductOptionGroupResponse createOptionGroup(
        @PathVariable UUID productId,
        @Valid @RequestBody
        CreateProductOptionGroupRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.createOptionGroup(
            userId(authentication),
            productId,
            request
        );
    }

    @PostMapping(
        "/{productId}/option-groups/{groupId}/options"
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('product.write')")
    public ProductOptionResponse createOption(
        @PathVariable UUID productId,
        @PathVariable UUID groupId,
        @Valid @RequestBody
        CreateProductOptionRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.createOption(
            userId(authentication),
            productId,
            groupId,
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