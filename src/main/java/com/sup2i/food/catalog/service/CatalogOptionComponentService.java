package com.sup2i.food.catalog.service;

import com.sup2i.food.catalog.api.dto.CreateProductOptionComponentRequest;
import com.sup2i.food.catalog.api.dto.ProductOptionComponentResponse;
import com.sup2i.food.catalog.domain.Ingredient;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductOption;
import com.sup2i.food.catalog.domain.ProductOptionComponent;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.catalog.exception.CatalogConflictException;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;
import com.sup2i.food.catalog.repository.IngredientRepository;
import com.sup2i.food.catalog.repository.ProductOptionComponentRepository;
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
public class CatalogOptionComponentService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository optionRepository;

    private final ProductOptionComponentRepository
        componentRepository;

    private final ProductVariantRepository variantRepository;
    private final IngredientRepository ingredientRepository;

    public CatalogOptionComponentService(
        UserRepository userRepository,
        ProductRepository productRepository,
        ProductOptionRepository optionRepository,
        ProductOptionComponentRepository componentRepository,
        ProductVariantRepository variantRepository,
        IngredientRepository ingredientRepository
    ) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.optionRepository = optionRepository;
        this.componentRepository = componentRepository;
        this.variantRepository = variantRepository;
        this.ingredientRepository = ingredientRepository;
    }

    @Transactional
    public ProductOptionComponentResponse create(
        UUID actorId,
        UUID productId,
        UUID optionId,
        CreateProductOptionComponentRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization().getId();

        productForOrganization(
            productId,
            organizationId
        );

        ProductOption option =
            optionForProduct(
                optionId,
                productId
            );

        Product componentProduct = null;
        ProductVariant componentVariant = null;
        Ingredient ingredient = null;

        if (request.componentProductId() != null) {
            componentProduct =
                productForOrganization(
                    request.componentProductId(),
                    organizationId
                );
        }

        if (request.componentVariantId() != null) {

            componentVariant =
                variantRepository
                    .findById(
                        request.componentVariantId()
                    )
                    .orElseThrow(() ->
                        new CatalogNotFoundException(
                            "Component variant does not exist."
                        )
                    );

            if (
                !componentVariant
                    .getProduct()
                    .getOrganization()
                    .getId()
                    .equals(organizationId)
            ) {
                throw new CatalogNotFoundException(
                    "Component variant does not exist."
                );
            }
        }

        if (request.ingredientId() != null) {
            ingredient =
                ingredientRepository
                    .findByIdAndOrganization_Id(
                        request.ingredientId(),
                        organizationId
                    )
                    .orElseThrow(() ->
                        new CatalogNotFoundException(
                            "Ingredient does not exist."
                        )
                    );
        }

        ProductOptionComponent component =
            new ProductOptionComponent(
                option,
                componentProduct,
                componentVariant,
                ingredient,
                request.quantity() == null
                    ? BigDecimal.ONE
                    : request.quantity(),
                request.unit() == null
                    ? com.sup2i.food.common.domain.MeasurementUnit.PIECE
                    : request.unit()
            );

        try {
            component =
                componentRepository
                    .saveAndFlush(component);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Product option component conflicts with an existing resource."
            );
        }

        return response(component);
    }

    @Transactional(readOnly = true)
    public List<ProductOptionComponentResponse> findAll(
        UUID userId,
        UUID productId,
        UUID optionId
    ) {

        User user =
            authenticatedUser(userId);

        productForOrganization(
            productId,
            user.getOrganization().getId()
        );

        ProductOption option =
            optionForProduct(
                optionId,
                productId
            );

        return componentRepository
            .findAllByProductOption_IdOrderByIdAsc(
                option.getId()
            )
            .stream()
            .map(this::response)
            .toList();
    }

    private ProductOption optionForProduct(
        UUID optionId,
        UUID productId
    ) {

        return optionRepository
            .findByIdAndOptionGroup_Product_Id(
                optionId,
                productId
            )
            .orElseThrow(() ->
                new CatalogNotFoundException(
                    "Product option does not exist."
                )
            );
    }

    private Product productForOrganization(
        UUID productId,
        UUID organizationId
    ) {

        return productRepository
            .findCatalogProduct(
                productId,
                organizationId
            )
            .orElseThrow(() ->
                new CatalogNotFoundException(
                    "Product does not exist."
                )
            );
    }

    private User authenticatedUser(
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

    private ProductOptionComponentResponse response(
        ProductOptionComponent component
    ) {

        return new ProductOptionComponentResponse(
            component.getId(),
            component.getProductOption().getId(),
            component.getComponentProduct() == null
                ? null
                : component.getComponentProduct().getId(),
            component.getComponentProduct() == null
                ? null
                : component.getComponentProduct().getName(),
            component.getComponentVariant() == null
                ? null
                : component.getComponentVariant().getId(),
            component.getComponentVariant() == null
                ? null
                : component.getComponentVariant().getName(),
            component.getIngredient() == null
                ? null
                : component.getIngredient().getId(),
            component.getIngredient() == null
                ? null
                : component.getIngredient().getName(),
            component.getQuantity(),
            component.getUnit()
        );
    }
}