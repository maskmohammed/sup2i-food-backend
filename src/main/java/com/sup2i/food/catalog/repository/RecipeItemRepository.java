package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.RecipeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecipeItemRepository
    extends JpaRepository<RecipeItem, UUID> {

    List<RecipeItem>
        findAllByRecipe_IdOrderByIngredient_NameAsc(
            UUID recipeId
        );
}