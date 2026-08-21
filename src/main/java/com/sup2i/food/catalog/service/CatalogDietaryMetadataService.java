package com.sup2i.food.catalog.service;

import com.sup2i.food.catalog.api.dto.DietaryReferenceResponse;
import com.sup2i.food.catalog.api.dto.ProductAllergenResponse;
import com.sup2i.food.catalog.api.dto.ProductDietaryMetadataResponse;
import com.sup2i.food.catalog.api.dto.ProductDietaryTagResponse;
import com.sup2i.food.catalog.api.dto.ProductReferenceLinkRequest;
import com.sup2i.food.catalog.api.dto.UpsertProductDietaryMetadataRequest;
import com.sup2i.food.catalog.domain.Allergen;
import com.sup2i.food.catalog.domain.DietaryTag;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductAllergen;
import com.sup2i.food.catalog.domain.ProductDietaryTag;
import com.sup2i.food.catalog.exception.CatalogConflictException;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;
import com.sup2i.food.catalog.exception.ProductUnavailableException;
import com.sup2i.food.catalog.repository.AllergenRepository;
import com.sup2i.food.catalog.repository.DietaryTagRepository;
import com.sup2i.food.catalog.repository.ProductAllergenRepository;
import com.sup2i.food.catalog.repository.ProductDietaryTagRepository;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CatalogDietaryMetadataService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final AllergenRepository allergenRepository;
    private final DietaryTagRepository dietaryTagRepository;

    private final ProductAllergenRepository
        productAllergenRepository;

    private final ProductDietaryTagRepository
        productDietaryTagRepository;

    public CatalogDietaryMetadataService(
        UserRepository userRepository,
        ProductRepository productRepository,
        AllergenRepository allergenRepository,
        DietaryTagRepository dietaryTagRepository,
        ProductAllergenRepository productAllergenRepository,
        ProductDietaryTagRepository productDietaryTagRepository
    ) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.allergenRepository = allergenRepository;
        this.dietaryTagRepository = dietaryTagRepository;
        this.productAllergenRepository =
            productAllergenRepository;
        this.productDietaryTagRepository =
            productDietaryTagRepository;
    }

    @Transactional(readOnly = true)
    public List<DietaryReferenceResponse> allergens(
        UUID userId
    ) {

        UUID organizationId =
            authenticatedUser(userId)
                .getOrganization()
                .getId();

        return allergenRepository
            .findAllByOrganization_IdAndActiveTrueOrderByNameAsc(
                organizationId
            )
            .stream()
            .map(allergen ->
                new DietaryReferenceResponse(
                    allergen.getId(),
                    allergen.getCode(),
                    allergen.getName(),
                    allergen.getDescription()
                )
            )
            .toList();
    }

    @Transactional(readOnly = true)
    public List<DietaryReferenceResponse> dietaryTags(
        UUID userId
    ) {

        UUID organizationId =
            authenticatedUser(userId)
                .getOrganization()
                .getId();

        return dietaryTagRepository
            .findAllByOrganization_IdAndActiveTrueOrderByNameAsc(
                organizationId
            )
            .stream()
            .map(tag ->
                new DietaryReferenceResponse(
                    tag.getId(),
                    tag.getCode(),
                    tag.getName(),
                    tag.getDescription()
                )
            )
            .toList();
    }

    @Transactional(readOnly = true)
    public ProductDietaryMetadataResponse metadata(
        UUID userId,
        UUID productId
    ) {

        User user =
            authenticatedUser(userId);

        Product product =
            productForOrganization(
                productId,
                user.getOrganization().getId()
            );

        if (
            !product.isActive()
            || !product.getCategory().isActive()
        ) {
            throw new ProductUnavailableException(
                "Product is not available."
            );
        }

        return response(productId);
    }

    @Transactional
    public ProductDietaryMetadataResponse replace(
        UUID actorId,
        UUID productId,
        UpsertProductDietaryMetadataRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization().getId();

        Product product =
            productForOrganization(
                productId,
                organizationId
            );

        Map<UUID, Allergen> allergens =
            resolveAllergens(
                request.allergens(),
                organizationId
            );

        Map<UUID, DietaryTag> dietaryTags =
            resolveDietaryTags(
                request.dietaryTags(),
                organizationId
            );

        try {
            productAllergenRepository
                .deleteAllByProduct_Id(
                    productId
                );

            productAllergenRepository.flush();

            List<ProductAllergen>
                productAllergens =
                    request.allergens()
                        .stream()
                        .map(link ->
                            new ProductAllergen(
                                product,
                                allergens.get(
                                    link.referenceId()
                                ),
                                normalizeNote(
                                    link.note()
                                )
                            )
                        )
                        .toList();

            productAllergenRepository
                .saveAllAndFlush(
                    productAllergens
                );

            productDietaryTagRepository
                .deleteAllByProduct_Id(
                    productId
                );

            productDietaryTagRepository.flush();

            List<ProductDietaryTag>
                productDietaryTags =
                    request.dietaryTags()
                        .stream()
                        .map(link ->
                            new ProductDietaryTag(
                                product,
                                dietaryTags.get(
                                    link.referenceId()
                                ),
                                normalizeNote(
                                    link.note()
                                )
                            )
                        )
                        .toList();

            productDietaryTagRepository
                .saveAllAndFlush(
                    productDietaryTags
                );
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Product dietary metadata conflicts with an existing resource."
            );
        }

        return response(productId);
    }

    private Map<UUID, Allergen> resolveAllergens(
        List<ProductReferenceLinkRequest> links,
        UUID organizationId
    ) {

        if (links.isEmpty()) {
            return Map.of();
        }

        List<UUID> ids =
            links.stream()
                .map(
                    ProductReferenceLinkRequest::referenceId
                )
                .toList();

        List<Allergen> values =
            allergenRepository
                .findAllByIdInAndOrganization_IdAndActiveTrue(
                    ids,
                    organizationId
                );

        if (values.size() != ids.size()) {
            throw new CatalogNotFoundException(
                "One or more allergens do not exist."
            );
        }

        Map<UUID, Allergen> byId =
            new LinkedHashMap<>();

        values.forEach(
            value ->
                byId.put(
                    value.getId(),
                    value
                )
        );

        return byId;
    }

    private Map<UUID, DietaryTag> resolveDietaryTags(
        List<ProductReferenceLinkRequest> links,
        UUID organizationId
    ) {

        if (links.isEmpty()) {
            return Map.of();
        }

        List<UUID> ids =
            links.stream()
                .map(
                    ProductReferenceLinkRequest::referenceId
                )
                .toList();

        List<DietaryTag> values =
            dietaryTagRepository
                .findAllByIdInAndOrganization_IdAndActiveTrue(
                    ids,
                    organizationId
                );

        if (values.size() != ids.size()) {
            throw new CatalogNotFoundException(
                "One or more dietary tags do not exist."
            );
        }

        Map<UUID, DietaryTag> byId =
            new LinkedHashMap<>();

        values.forEach(
            value ->
                byId.put(
                    value.getId(),
                    value
                )
        );

        return byId;
    }

    private ProductDietaryMetadataResponse response(
        UUID productId
    ) {

        List<ProductAllergenResponse> allergens =
            productAllergenRepository
                .findAllByProduct_IdAndAllergen_ActiveTrueOrderByAllergen_NameAsc(
                    productId
                )
                .stream()
                .map(link ->
                    new ProductAllergenResponse(
                        link.getAllergen().getId(),
                        link.getAllergen().getCode(),
                        link.getAllergen().getName(),
                        link.getNote()
                    )
                )
                .toList();

        List<ProductDietaryTagResponse> tags =
            productDietaryTagRepository
                .findAllByProduct_IdAndDietaryTag_ActiveTrueOrderByDietaryTag_NameAsc(
                    productId
                )
                .stream()
                .map(link ->
                    new ProductDietaryTagResponse(
                        link.getDietaryTag().getId(),
                        link.getDietaryTag().getCode(),
                        link.getDietaryTag().getName(),
                        link.getNote()
                    )
                )
                .toList();

        return new ProductDietaryMetadataResponse(
            productId,
            allergens,
            tags
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

    private String normalizeNote(
        String note
    ) {

        if (note == null) {
            return null;
        }

        String normalized =
            note.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }
}