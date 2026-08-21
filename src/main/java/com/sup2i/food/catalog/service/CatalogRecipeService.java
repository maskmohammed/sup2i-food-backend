package com.sup2i.food.catalog.service;

import com.sup2i.food.catalog.api.dto.CreateRecipeVersionRequest;
import com.sup2i.food.catalog.api.dto.RecipeItemRequest;
import com.sup2i.food.catalog.api.dto.RecipeItemResponse;
import com.sup2i.food.catalog.api.dto.RecipeResponse;
import com.sup2i.food.catalog.domain.Ingredient;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.catalog.domain.Recipe;
import com.sup2i.food.catalog.domain.RecipeItem;
import com.sup2i.food.catalog.exception.CatalogConflictException;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;
import com.sup2i.food.catalog.repository.IngredientRepository;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.catalog.repository.ProductVariantRepository;
import com.sup2i.food.catalog.repository.RecipeItemRepository;
import com.sup2i.food.catalog.repository.RecipeRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CatalogRecipeService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    private final ProductVariantRepository
        variantRepository;

    private final IngredientRepository ingredientRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeItemRepository recipeItemRepository;

    public CatalogRecipeService(
        UserRepository userRepository,
        ProductRepository productRepository,
        ProductVariantRepository variantRepository,
        IngredientRepository ingredientRepository,
        RecipeRepository recipeRepository,
        RecipeItemRepository recipeItemRepository
    ) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.ingredientRepository = ingredientRepository;
        this.recipeRepository = recipeRepository;
        this.recipeItemRepository = recipeItemRepository;
    }

    @Transactional
    public RecipeResponse createVersion(
        UUID actorId,
        UUID productId,
        CreateRecipeVersionRequest request
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

        ProductVariant variant =
            resolveVariant(
                productId,
                request.variantId()
            );

        Map<UUID, Ingredient> ingredients =
            resolveIngredients(
                request.items(),
                organizationId
            );

        Integer maximumVersion =
            recipeRepository
                .findMaxVersion(
                    productId,
                    request.variantId()
                );

        int nextVersion =
            (maximumVersion == null
                ? 0
                : maximumVersion)
            + 1;

        OffsetDateTime now =
            OffsetDateTime.now();

        try {
            recipeRepository
                .findCurrent(
                    productId,
                    request.variantId()
                )
                .ifPresent(current -> {
                    current.close(now);

                    recipeRepository
                        .saveAndFlush(current);
                });

            Recipe recipe =
                recipeRepository
                    .saveAndFlush(
                        new Recipe(
                            product,
                            variant,
                            nextVersion,
                            now
                        )
                    );

            List<RecipeItem> items =
                request.items()
                    .stream()
                    .map(item ->
                        new RecipeItem(
                            recipe,
                            ingredients.get(
                                item.ingredientId()
                            ),
                            item.quantity(),
                            item.unit(),
                            item.wasteFactor(),
                            item.critical() == null
                                || item.critical()
                        )
                    )
                    .toList();

            recipeItemRepository
                .saveAllAndFlush(items);

            return response(recipe);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Recipe version conflicts with an existing resource."
            );
        }
    }

    @Transactional(readOnly = true)
    public RecipeResponse current(
        UUID userId,
        UUID productId,
        UUID variantId
    ) {

        User user =
            authenticatedUser(userId);

        UUID organizationId =
            user.getOrganization().getId();

        productForOrganization(
            productId,
            organizationId
        );

        resolveVariant(
            productId,
            variantId
        );

        Recipe recipe =
            recipeRepository
                .findCurrent(
                    productId,
                    variantId
                )
                .orElseThrow(() ->
                    new CatalogNotFoundException(
                        "Active recipe does not exist."
                    )
                );

        return response(recipe);
    }

    private Map<UUID, Ingredient> resolveIngredients(
        List<RecipeItemRequest> requestedItems,
        UUID organizationId
    ) {

        List<UUID> ids =
            requestedItems.stream()
                .map(
                    RecipeItemRequest::ingredientId
                )
                .toList();

        List<Ingredient> ingredients =
            ingredientRepository
                .findAllByIdInAndOrganization_Id(
                    ids,
                    organizationId
                );

        if (ingredients.size() != ids.size()) {
            throw new CatalogNotFoundException(
                "One or more ingredients do not exist."
            );
        }

        Map<UUID, Ingredient> byId =
            new LinkedHashMap<>();

        ingredients.forEach(
            ingredient ->
                byId.put(
                    ingredient.getId(),
                    ingredient
                )
        );

        return byId;
    }

    private ProductVariant resolveVariant(
        UUID productId,
        UUID variantId
    ) {

        if (variantId == null) {
            return null;
        }

        return variantRepository
            .findByIdAndProduct_Id(
                variantId,
                productId
            )
            .orElseThrow(() ->
                new CatalogNotFoundException(
                    "Variant does not belong to the selected product."
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

    private RecipeResponse response(
        Recipe recipe
    ) {

        List<RecipeItemResponse> items =
            recipeItemRepository
                .findAllByRecipe_IdOrderByIngredient_NameAsc(
                    recipe.getId()
                )
                .stream()
                .map(item ->
                    new RecipeItemResponse(
                        item.getId(),
                        item.getIngredient().getId(),
                        item.getIngredient().getCode(),
                        item.getIngredient().getName(),
                        item.getQuantity(),
                        item.getUnit(),
                        item.getWasteFactor(),
                        item.isCritical()
                    )
                )
                .toList();

        return new RecipeResponse(
            recipe.getId(),
            recipe.getProduct().getId(),
            recipe.getVariant() == null
                ? null
                : recipe.getVariant().getId(),
            recipe.getVariant() == null
                ? null
                : recipe.getVariant().getName(),
            recipe.getVersion(),
            recipe.isActive(),
            recipe.getEffectiveFrom(),
            recipe.getEffectiveTo(),
            items
        );
    }
}