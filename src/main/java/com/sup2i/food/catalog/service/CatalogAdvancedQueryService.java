package com.sup2i.food.catalog.service;

import com.sup2i.food.catalog.api.dto.ProductConfigurationResponse;
import com.sup2i.food.catalog.api.dto.ProductOptionGroupResponse;
import com.sup2i.food.catalog.api.dto.ProductOptionResponse;
import com.sup2i.food.catalog.api.dto.ProductVariantResponse;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductOption;
import com.sup2i.food.catalog.domain.ProductOptionGroup;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;
import com.sup2i.food.catalog.exception.ProductUnavailableException;
import com.sup2i.food.catalog.repository.ProductOptionGroupRepository;
import com.sup2i.food.catalog.repository.ProductOptionRepository;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.catalog.repository.ProductVariantRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CatalogAdvancedQueryService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductOptionGroupRepository optionGroupRepository;
    private final ProductOptionRepository optionRepository;

    public CatalogAdvancedQueryService(
        UserRepository userRepository,
        ProductRepository productRepository,
        ProductVariantRepository variantRepository,
        ProductOptionGroupRepository optionGroupRepository,
        ProductOptionRepository optionRepository
    ) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.optionRepository = optionRepository;
    }

    @Transactional(readOnly = true)
    public ProductConfigurationResponse configuration(
        UUID userId,
        UUID productId
    ) {
        User user =
            userRepository
                .findById(userId)
                .orElseThrow(() ->
                    new BadCredentialsException(
                        "Authenticated user does not exist."
                    )
                );

        Product product =
            productRepository
                .findCatalogProduct(
                    productId,
                    user.getOrganization().getId()
                )
                .orElseThrow(() ->
                    new CatalogNotFoundException(
                        "Product does not exist."
                    )
                );

        if (
            !product.isActive()
            || !product.getCategory().isActive()
        ) {
            throw new ProductUnavailableException(
                "Product is not available."
            );
        }

        List<ProductVariantResponse> variants =
            variantRepository
                .findAllByProduct_IdAndActiveTrueOrderByDisplayOrderAscNameAsc(
                    productId
                )
                .stream()
                .map(this::toVariantResponse)
                .toList();

        List<ProductOptionGroup> groups =
            optionGroupRepository
                .findAllByProduct_IdOrderByDisplayOrderAscNameAsc(
                    productId
                );

        List<UUID> groupIds =
            groups.stream()
                .map(ProductOptionGroup::getId)
                .toList();

        List<ProductOption> options =
            groupIds.isEmpty()
                ? List.of()
                : optionRepository
                    .findAllByOptionGroup_IdInAndActiveTrueOrderByOptionGroup_IdAscDisplayOrderAscNameAsc(
                        groupIds
                    );

        Map<UUID, List<ProductOptionResponse>>
            optionsByGroup =
                new LinkedHashMap<>();

        for (ProductOption option : options) {
            optionsByGroup
                .computeIfAbsent(
                    option.getOptionGroup().getId(),
                    ignored ->
                        new ArrayList<>()
                )
                .add(
                    toOptionResponse(option)
                );
        }

        List<ProductOptionGroupResponse>
            optionGroups =
                groups.stream()
                    .map(group ->
                        new ProductOptionGroupResponse(
                            group.getId(),
                            group.getName(),
                            group.getMinSelect(),
                            group.getMaxSelect(),
                            group.isRequired(),
                            group.getDisplayOrder(),
                            optionsByGroup.getOrDefault(
                                group.getId(),
                                List.of()
                            )
                        )
                    )
                    .toList();

        return new ProductConfigurationResponse(
            productId,
            variants,
            optionGroups
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