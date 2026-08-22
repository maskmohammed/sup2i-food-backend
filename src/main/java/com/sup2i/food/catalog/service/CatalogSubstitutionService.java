package com.sup2i.food.catalog.service;

import com.sup2i.food.catalog.api.dto.ProductSubstitutionResponse;
import com.sup2i.food.catalog.api.dto.UpsertProductSubstitutionRequest;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductSubstitution;
import com.sup2i.food.catalog.exception.CatalogConflictException;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;
import com.sup2i.food.catalog.exception.CatalogValidationException;
import com.sup2i.food.catalog.exception.ProductUnavailableException;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.catalog.repository.ProductSubstitutionRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CatalogSubstitutionService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    private final ProductSubstitutionRepository
        substitutionRepository;

    public CatalogSubstitutionService(
        UserRepository userRepository,
        ProductRepository productRepository,
        ProductSubstitutionRepository substitutionRepository
    ) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.substitutionRepository =
            substitutionRepository;
    }

    @Transactional
    public ProductSubstitutionResponse upsert(
        UUID actorId,
        UUID productId,
        UUID substituteProductId,
        UpsertProductSubstitutionRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        Product product =
            productForOrganization(
                productId,
                organizationId
            );

        Product substituteProduct =
            productForOrganization(
                substituteProductId,
                organizationId
            );

        if (
            product.getId()
                .equals(
                    substituteProduct.getId()
                )
        ) {
            throw new CatalogValidationException(
                "A product cannot substitute itself."
            );
        }

        ProductSubstitution substitution =
            substitutionRepository
                .findByProduct_IdAndSubstituteProduct_Id(
                    productId,
                    substituteProductId
                )
                .map(existing -> {

                    existing.update(
                        request.priority() == null
                            ? existing.getPriority()
                            : request.priority(),
                        request.active() == null
                            ? existing.isActive()
                            : request.active()
                    );

                    return existing;
                })
                .orElseGet(() ->
                    new ProductSubstitution(
                        product,
                        substituteProduct,
                        request.priority() == null
                            ? 0
                            : request.priority(),
                        request.active() == null
                            || request.active()
                    )
                );

        try {
            substitution =
                substitutionRepository
                    .saveAndFlush(
                        substitution
                    );
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Product substitution conflicts with an existing resource."
            );
        }

        return response(
            substitution
        );
    }

    @Transactional(readOnly = true)
    public List<ProductSubstitutionResponse>
        adminFindAll(
            UUID userId,
            UUID productId
        ) {

        User user =
            authenticatedUser(userId);

        productForOrganization(
            productId,
            user.getOrganization()
                .getId()
        );

        return substitutionRepository
            .findAllByProduct_IdOrderByPriorityAscSubstituteProduct_NameAsc(
                productId
            )
            .stream()
            .map(this::response)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductSubstitutionResponse>
        catalogFindAll(
            UUID userId,
            UUID productId
        ) {

        User user =
            authenticatedUser(userId);

        UUID organizationId =
            user.getOrganization()
                .getId();

        Product product =
            productForOrganization(
                productId,
                organizationId
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

        return substitutionRepository
            .findAllByProduct_IdOrderByPriorityAscSubstituteProduct_NameAsc(
                productId
            )
            .stream()
            .filter(
                ProductSubstitution::isActive
            )
            .filter(substitution ->
                substitution
                    .getSubstituteProduct()
                    .getOrganization()
                    .getId()
                    .equals(
                        organizationId
                    )
            )
            .filter(substitution ->
                substitution
                    .getSubstituteProduct()
                    .isActive()
            )
            .filter(substitution ->
                substitution
                    .getSubstituteProduct()
                    .getCategory()
                    .isActive()
            )
            .map(this::response)
            .toList();
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

    private ProductSubstitutionResponse response(
        ProductSubstitution substitution
    ) {

        return new ProductSubstitutionResponse(
            substitution
                .getProduct()
                .getId(),
            substitution
                .getSubstituteProduct()
                .getId(),
            substitution
                .getSubstituteProduct()
                .getName(),
            substitution.getPriority(),
            substitution.isActive(),
            substitution.getCreatedAt()
        );
    }
}