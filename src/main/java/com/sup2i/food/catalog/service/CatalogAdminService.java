package com.sup2i.food.catalog.service;

import com.sup2i.food.catalog.api.dto.CategoryResponse;
import com.sup2i.food.catalog.api.dto.CreateCategoryRequest;
import com.sup2i.food.catalog.api.dto.CreateProductRequest;
import com.sup2i.food.catalog.api.dto.ProductResponse;
import com.sup2i.food.catalog.domain.Category;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductPriceHistory;
import com.sup2i.food.catalog.exception.CatalogConflictException;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;
import com.sup2i.food.catalog.repository.CategoryRepository;
import com.sup2i.food.catalog.repository.ProductPriceHistoryRepository;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class CatalogAdminService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductPriceHistoryRepository
        priceHistoryRepository;
    private final CatalogNormalizationService
        normalizationService;

    public CatalogAdminService(
        UserRepository userRepository,
        CategoryRepository categoryRepository,
        ProductRepository productRepository,
        ProductPriceHistoryRepository priceHistoryRepository,
        CatalogNormalizationService normalizationService
    ) {
        this.userRepository =
            userRepository;

        this.categoryRepository =
            categoryRepository;

        this.productRepository =
            productRepository;

        this.priceHistoryRepository =
            priceHistoryRepository;

        this.normalizationService =
            normalizationService;
    }

    @Transactional
    public CategoryResponse createCategory(
        UUID actorUserId,
        CreateCategoryRequest request
    ) {

        User actor =
            actor(actorUserId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        String slug =
            normalizationService
                .normalizeSlug(
                    request.slug(),
                    request.name()
                );

        if (
            categoryRepository
                .existsByOrganization_IdAndSlugIgnoreCase(
                    organizationId,
                    slug
                )
        ) {
            throw new CatalogConflictException(
                "Category slug already exists."
            );
        }

        Category parent = null;

        if (request.parentId() != null) {

            parent =
                categoryRepository
                    .findByIdAndOrganization_Id(
                        request.parentId(),
                        organizationId
                    )
                    .orElseThrow(() ->
                        new CatalogNotFoundException(
                            "Parent category does not exist."
                        )
                    );
        }

        boolean active =
            request.active() == null
                || request.active();

        Category category =
            new Category(
                actor.getOrganization(),
                parent,
                request.name().trim(),
                slug,
                request.displayOrder(),
                active
            );

        try {
            category =
                categoryRepository.saveAndFlush(
                    category
                );
        }
        catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Category conflicts with an existing resource."
            );
        }

        return new CategoryResponse(
            category.getId(),
            parent == null
                ? null
                : parent.getId(),
            category.getName(),
            category.getSlug(),
            category.getDisplayOrder(),
            category.isActive()
        );
    }

    @Transactional
    public ProductResponse createProduct(
        UUID actorUserId,
        CreateProductRequest request
    ) {

        User actor =
            actor(actorUserId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        Category category =
            categoryRepository
                .findByIdAndOrganization_Id(
                    request.categoryId(),
                    organizationId
                )
                .orElseThrow(() ->
                    new CatalogNotFoundException(
                        "Category does not exist."
                    )
                );

        String sku =
            normalizationService
                .normalizeSku(
                    request.sku()
                );

        if (
            productRepository
                .existsByOrganization_IdAndSkuIgnoreCase(
                    organizationId,
                    sku
                )
        ) {
            throw new CatalogConflictException(
                "Product SKU already exists."
            );
        }

        boolean trackStock =
            request.trackStock() == null
                || request.trackStock();

        boolean prepared =
            request.prepared() != null
                && request.prepared();

        boolean active =
            request.active() == null
                || request.active();

        Product product =
            new Product(
                actor.getOrganization(),
                category,
                sku,
                request.name().trim(),
                trimToNull(
                    request.description()
                ),
                trimToNull(
                    request.imageUrl()
                ),
                request.productType(),
                request.basePrice(),
                request.taxRate(),
                request.preparationMinutes(),
                trackStock,
                prepared,
                active
            );

        try {
            product =
                productRepository.saveAndFlush(
                    product
                );
        }
        catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Product conflicts with an existing resource."
            );
        }

        ProductPriceHistory history =
            new ProductPriceHistory(
                product,
                product.getBasePrice(),
                product.getTaxRate(),
                OffsetDateTime.now(),
                actor,
                "INITIAL_PRODUCT_PRICE"
            );

        priceHistoryRepository.save(
            history
        );

        return new ProductResponse(
            product.getId(),
            category.getId(),
            category.getName(),
            product.getSku(),
            product.getName(),
            product.getDescription(),
            product.getImageUrl(),
            product.getProductType(),
            product.getBasePrice(),
            product.getTaxRate(),
            product.getPreparationMinutes(),
            product.isTrackStock(),
            product.isPrepared(),
            product.isActive()
        );
    }

    private User actor(
        UUID userId
    ) {
        return userRepository
            .findById(userId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user does not exist."
                )
            );
    }

    private String trimToNull(
        String value
    ) {
        if (
            value == null
            || value.isBlank()
        ) {
            return null;
        }

        return value.trim();
    }
}