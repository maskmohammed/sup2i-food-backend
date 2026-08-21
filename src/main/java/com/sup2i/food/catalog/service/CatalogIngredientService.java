package com.sup2i.food.catalog.service;

import com.sup2i.food.catalog.api.dto.CreateIngredientRequest;
import com.sup2i.food.catalog.api.dto.DietaryReferenceResponse;
import com.sup2i.food.catalog.api.dto.IngredientResponse;
import com.sup2i.food.catalog.api.dto.ReplaceIngredientAllergensRequest;
import com.sup2i.food.catalog.domain.Allergen;
import com.sup2i.food.catalog.domain.Ingredient;
import com.sup2i.food.catalog.domain.IngredientAllergen;
import com.sup2i.food.catalog.exception.CatalogConflictException;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;
import com.sup2i.food.catalog.repository.AllergenRepository;
import com.sup2i.food.catalog.repository.IngredientAllergenRepository;
import com.sup2i.food.catalog.repository.IngredientRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CatalogIngredientService {

    private final UserRepository userRepository;
    private final IngredientRepository ingredientRepository;
    private final AllergenRepository allergenRepository;

    private final IngredientAllergenRepository
        ingredientAllergenRepository;

    public CatalogIngredientService(
        UserRepository userRepository,
        IngredientRepository ingredientRepository,
        AllergenRepository allergenRepository,
        IngredientAllergenRepository ingredientAllergenRepository
    ) {
        this.userRepository = userRepository;
        this.ingredientRepository = ingredientRepository;
        this.allergenRepository = allergenRepository;
        this.ingredientAllergenRepository =
            ingredientAllergenRepository;
    }

    @Transactional
    public IngredientResponse create(
        UUID actorId,
        CreateIngredientRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization().getId();

        String code =
            normalizeCode(
                request.code()
            );

        if (
            ingredientRepository
                .existsByOrganization_IdAndCode(
                    organizationId,
                    code
                )
        ) {
            throw new CatalogConflictException(
                "Ingredient code already exists."
            );
        }

        Ingredient ingredient =
            new Ingredient(
                actor.getOrganization(),
                code,
                request.name().trim(),
                request.baseUnit(),
                request.active() == null
                    || request.active()
            );

        try {
            ingredient =
                ingredientRepository
                    .saveAndFlush(
                        ingredient
                    );
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Ingredient conflicts with an existing resource."
            );
        }

        return response(ingredient);
    }

    @Transactional(readOnly = true)
    public List<IngredientResponse> findAll(
        UUID userId
    ) {

        UUID organizationId =
            authenticatedUser(userId)
                .getOrganization()
                .getId();

        return ingredientRepository
            .findAllByOrganization_IdAndActiveTrueOrderByNameAsc(
                organizationId
            )
            .stream()
            .map(this::response)
            .toList();
    }

    @Transactional(readOnly = true)
    public IngredientResponse findOne(
        UUID userId,
        UUID ingredientId
    ) {

        User user =
            authenticatedUser(userId);

        Ingredient ingredient =
            ingredientForOrganization(
                ingredientId,
                user.getOrganization().getId()
            );

        return response(ingredient);
    }

    @Transactional
    public IngredientResponse replaceAllergens(
        UUID actorId,
        UUID ingredientId,
        ReplaceIngredientAllergensRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization().getId();

        Ingredient ingredient =
            ingredientForOrganization(
                ingredientId,
                organizationId
            );

        List<UUID> ids =
            request.allergenIds();

        List<Allergen> allergens =
            ids.isEmpty()
                ? List.of()
                : allergenRepository
                    .findAllByIdInAndOrganization_IdAndActiveTrue(
                        ids,
                        organizationId
                    );

        if (allergens.size() != ids.size()) {
            throw new CatalogNotFoundException(
                "One or more allergens do not exist."
            );
        }

        Map<UUID, Allergen> byId =
            new LinkedHashMap<>();

        allergens.forEach(
            allergen ->
                byId.put(
                    allergen.getId(),
                    allergen
                )
        );

        try {
            ingredientAllergenRepository
                .deleteAllByIngredient_Id(
                    ingredientId
                );

            ingredientAllergenRepository
                .flush();

            List<IngredientAllergen> links =
                ids.stream()
                    .map(allergenId ->
                        new IngredientAllergen(
                            ingredient,
                            byId.get(allergenId)
                        )
                    )
                    .toList();

            ingredientAllergenRepository
                .saveAllAndFlush(links);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Ingredient allergens conflict with an existing resource."
            );
        }

        return response(ingredient);
    }

    private Ingredient ingredientForOrganization(
        UUID ingredientId,
        UUID organizationId
    ) {

        return ingredientRepository
            .findByIdAndOrganization_Id(
                ingredientId,
                organizationId
            )
            .orElseThrow(() ->
                new CatalogNotFoundException(
                    "Ingredient does not exist."
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

    private IngredientResponse response(
        Ingredient ingredient
    ) {

        List<DietaryReferenceResponse> allergens =
            ingredientAllergenRepository
                .findAllByIngredient_IdAndAllergen_ActiveTrueOrderByAllergen_NameAsc(
                    ingredient.getId()
                )
                .stream()
                .map(link ->
                    new DietaryReferenceResponse(
                        link.getAllergen().getId(),
                        link.getAllergen().getCode(),
                        link.getAllergen().getName(),
                        link.getAllergen().getDescription()
                    )
                )
                .toList();

        return new IngredientResponse(
            ingredient.getId(),
            ingredient.getCode(),
            ingredient.getName(),
            ingredient.getBaseUnit(),
            ingredient.isActive(),
            allergens
        );
    }

    private String normalizeCode(
        String code
    ) {

        return code.trim()
            .toUpperCase(
                Locale.ROOT
            );
    }
}