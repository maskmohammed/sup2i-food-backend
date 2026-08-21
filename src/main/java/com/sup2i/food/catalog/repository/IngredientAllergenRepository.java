package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.IngredientAllergen;
import com.sup2i.food.catalog.domain.IngredientAllergenId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IngredientAllergenRepository
    extends JpaRepository<
        IngredientAllergen,
        IngredientAllergenId
    > {

    List<IngredientAllergen>
        findAllByIngredient_IdAndAllergen_ActiveTrueOrderByAllergen_NameAsc(
            UUID ingredientId
        );

    void deleteAllByIngredient_Id(
        UUID ingredientId
    );
}