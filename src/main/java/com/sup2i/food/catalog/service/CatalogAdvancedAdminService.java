package com.sup2i.food.catalog.service;

import com.sup2i.food.catalog.api.dto.CreateProductBarcodeRequest;
import com.sup2i.food.catalog.api.dto.CreateProductOptionGroupRequest;
import com.sup2i.food.catalog.api.dto.CreateProductOptionRequest;
import com.sup2i.food.catalog.api.dto.CreateProductVariantRequest;
import com.sup2i.food.catalog.api.dto.ProductBarcodeResponse;
import com.sup2i.food.catalog.api.dto.ProductOptionGroupResponse;
import com.sup2i.food.catalog.api.dto.ProductOptionResponse;
import com.sup2i.food.catalog.api.dto.ProductVariantResponse;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductBarcode;
import com.sup2i.food.catalog.domain.ProductOption;
import com.sup2i.food.catalog.domain.ProductOptionGroup;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.catalog.exception.CatalogConflictException;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;
import com.sup2i.food.catalog.repository.ProductBarcodeRepository;
import com.sup2i.food.catalog.repository.ProductOptionGroupRepository;
import com.sup2i.food.catalog.repository.ProductOptionRepository;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.catalog.repository.ProductVariantRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CatalogAdvancedAdminService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductBarcodeRepository barcodeRepository;
    private final ProductOptionGroupRepository optionGroupRepository;
    private final ProductOptionRepository optionRepository;
    private final CatalogNormalizationService normalizationService;

    public CatalogAdvancedAdminService(
        UserRepository userRepository,
        ProductRepository productRepository,
        ProductVariantRepository variantRepository,
        ProductBarcodeRepository barcodeRepository,
        ProductOptionGroupRepository optionGroupRepository,
        ProductOptionRepository optionRepository,
        CatalogNormalizationService normalizationService
    ) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.barcodeRepository = barcodeRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.optionRepository = optionRepository;
        this.normalizationService = normalizationService;
    }

    @Transactional
    public ProductVariantResponse createVariant(
        UUID actorId,
        UUID productId,
        CreateProductVariantRequest request
    ) {
        Product product =
            productForActor(actorId, productId);

        String sku =
            request.sku() == null
                || request.sku().isBlank()
                    ? null
                    : normalizationService
                        .normalizeSku(request.sku());

        ProductVariant variant =
            new ProductVariant(
                product,
                request.name().trim(),
                sku,
                request.priceDelta() == null
                    ? BigDecimal.ZERO
                    : request.priceDelta(),
                request.active() == null
                    || request.active(),
                request.displayOrder()
            );

        try {
            variant =
                variantRepository.saveAndFlush(
                    variant
                );
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Product variant conflicts with an existing resource."
            );
        }

        return toVariantResponse(variant);
    }

    @Transactional
    public ProductBarcodeResponse createBarcode(
        UUID actorId,
        UUID productId,
        CreateProductBarcodeRequest request
    ) {
        Product product =
            productForActor(actorId, productId);

        String barcode =
            request.barcode().trim();

        if (
            barcodeRepository.existsByBarcode(
                barcode
            )
        ) {
            throw new CatalogConflictException(
                "Barcode already exists."
            );
        }

        ProductVariant variant = null;

        if (request.variantId() != null) {
            variant =
                variantRepository
                    .findByIdAndProduct_Id(
                        request.variantId(),
                        productId
                    )
                    .orElseThrow(() ->
                        new CatalogNotFoundException(
                            "Variant does not belong to this product."
                        )
                    );
        }

        ProductBarcode entity =
            new ProductBarcode(
                product,
                variant,
                barcode,
                request.packQuantity() == null
                    ? BigDecimal.ONE
                    : request.packQuantity(),
                request.primary() != null
                    && request.primary(),
                request.active() == null
                    || request.active()
            );

        try {
            entity =
                barcodeRepository.saveAndFlush(
                    entity
                );
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Barcode conflicts with an existing resource."
            );
        }

        return new ProductBarcodeResponse(
            entity.getId(),
            entity.getVariant() == null
                ? null
                : entity.getVariant().getId(),
            entity.getBarcode(),
            entity.getPackQuantity(),
            entity.isPrimary(),
            entity.isActive()
        );
    }

    @Transactional
    public ProductOptionGroupResponse createOptionGroup(
        UUID actorId,
        UUID productId,
        CreateProductOptionGroupRequest request
    ) {
        Product product =
            productForActor(actorId, productId);

        int min =
            request.minSelect() == null
                ? 0
                : request.minSelect();

        int max =
            request.maxSelect() == null
                ? 1
                : request.maxSelect();

        ProductOptionGroup group =
            new ProductOptionGroup(
                product,
                request.name().trim(),
                min,
                max,
                request.required() != null
                    && request.required(),
                request.displayOrder()
            );

        try {
            group =
                optionGroupRepository
                    .saveAndFlush(group);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Option group conflicts with an existing resource."
            );
        }

        return new ProductOptionGroupResponse(
            group.getId(),
            group.getName(),
            group.getMinSelect(),
            group.getMaxSelect(),
            group.isRequired(),
            group.getDisplayOrder(),
            List.of()
        );
    }

    @Transactional
    public ProductOptionResponse createOption(
        UUID actorId,
        UUID productId,
        UUID groupId,
        CreateProductOptionRequest request
    ) {
        productForActor(actorId, productId);

        ProductOptionGroup group =
            optionGroupRepository
                .findByIdAndProduct_Id(
                    groupId,
                    productId
                )
                .orElseThrow(() ->
                    new CatalogNotFoundException(
                        "Option group does not belong to this product."
                    )
                );

        ProductOption option =
            new ProductOption(
                group,
                request.name().trim(),
                request.priceDelta() == null
                    ? BigDecimal.ZERO
                    : request.priceDelta(),
                request.active() == null
                    || request.active(),
                request.displayOrder()
            );

        try {
            option =
                optionRepository
                    .saveAndFlush(option);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Product option conflicts with an existing resource."
            );
        }

        return toOptionResponse(option);
    }

    private Product productForActor(
        UUID actorId,
        UUID productId
    ) {
        User actor =
            userRepository
                .findById(actorId)
                .orElseThrow(() ->
                    new BadCredentialsException(
                        "Authenticated user does not exist."
                    )
                );

        return productRepository
            .findCatalogProduct(
                productId,
                actor.getOrganization().getId()
            )
            .orElseThrow(() ->
                new CatalogNotFoundException(
                    "Product does not exist."
                )
            );
    }

    private ProductVariantResponse toVariantResponse(
        ProductVariant variant
    ) {
        return new ProductVariantResponse(
            variant.getId(),
            variant.getName(),
            variant.getSku(),
            variant.getPriceDelta(),
            variant.isActive(),
            variant.getDisplayOrder()
        );
    }

    private ProductOptionResponse toOptionResponse(
        ProductOption option
    ) {
        return new ProductOptionResponse(
            option.getId(),
            option.getName(),
            option.getPriceDelta(),
            option.isActive(),
            option.getDisplayOrder()
        );
    }
}