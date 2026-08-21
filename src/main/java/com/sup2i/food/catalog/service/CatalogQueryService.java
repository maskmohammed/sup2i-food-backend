package com.sup2i.food.catalog.service;

import com.sup2i.food.catalog.api.dto.CategoryResponse;
import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.catalog.api.dto.ProductResponse;
import com.sup2i.food.catalog.domain.Category;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;
import com.sup2i.food.catalog.exception.ProductUnavailableException;
import com.sup2i.food.catalog.repository.CategoryRepository;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CatalogQueryService {

    private static final int MAX_PAGE_SIZE =
        100;

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CatalogQueryService(
        UserRepository userRepository,
        CategoryRepository categoryRepository,
        ProductRepository productRepository
    ) {
        this.userRepository =
            userRepository;

        this.categoryRepository =
            categoryRepository;

        this.productRepository =
            productRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse>
        categories(
            UUID userId
        ) {

        UUID organizationId =
            organizationId(userId);

        return categoryRepository
            .findAllByOrganization_IdAndActiveTrueOrderByDisplayOrderAscNameAsc(
                organizationId
            )
            .stream()
            .map(this::toCategoryResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse>
        products(
            UUID userId,
            UUID categoryId,
            int page,
            int size
        ) {

        UUID organizationId =
            organizationId(userId);

        int safePage =
            Math.max(page, 0);

        int safeSize =
            Math.min(
                Math.max(size, 1),
                MAX_PAGE_SIZE
            );

        PageRequest pageable =
            PageRequest.of(
                safePage,
                safeSize,
                Sort.by(
                    Sort.Order.asc("name"),
                    Sort.Order.asc("id")
                )
            );

        Page<ProductResponse> result =
            productRepository
                .findCatalogProducts(
                    organizationId,
                    categoryId,
                    pageable
                )
                .map(
                    this::toProductResponse
                );

        return PageResponse.from(
            result
        );
    }

    @Transactional(readOnly = true)
    public ProductResponse product(
        UUID userId,
        UUID productId
    ) {
        UUID organizationId =
            organizationId(userId);

        Product product =
            productRepository
                .findCatalogProduct(
                    productId,
                    organizationId
                )
                .orElseThrow(() ->
                    new CatalogNotFoundException(
                        "Product does not exist."
                    )
                );

        if (
            !product.isActive()
            || !product.getCategory()
                .isActive()
        ) {
            throw new ProductUnavailableException(
                "Product is not available."
            );
        }

        return toProductResponse(
            product
        );
    }

    private UUID organizationId(
        UUID userId
    ) {
        User user =
            userRepository
                .findById(userId)
                .orElseThrow(() ->
                    new BadCredentialsException(
                        "Authenticated user does not exist."
                    )
                );

        return user
            .getOrganization()
            .getId();
    }

    private CategoryResponse
        toCategoryResponse(
            Category category
        ) {

        return new CategoryResponse(
            category.getId(),
            category.getParent() == null
                ? null
                : category.getParent()
                    .getId(),
            category.getName(),
            category.getSlug(),
            category.getDisplayOrder(),
            category.isActive()
        );
    }

    private ProductResponse
        toProductResponse(
            Product product
        ) {

        return new ProductResponse(
            product.getId(),
            product.getCategory()
                .getId(),
            product.getCategory()
                .getName(),
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
}