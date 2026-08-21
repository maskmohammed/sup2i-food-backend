package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository
    extends JpaRepository<Recipe, UUID> {

    @Query("""
        select r
        from Recipe r
        where r.product.id = :productId
          and (
              (:variantId is null and r.variant is null)
              or r.variant.id = :variantId
          )
          and r.active = true
          and r.effectiveTo is null
        """)
    Optional<Recipe> findCurrent(
        @Param("productId")
        UUID productId,
        @Param("variantId")
        UUID variantId
    );

    @Query("""
        select max(r.version)
        from Recipe r
        where r.product.id = :productId
          and (
              (:variantId is null and r.variant is null)
              or r.variant.id = :variantId
          )
        """)
    Integer findMaxVersion(
        @Param("productId")
        UUID productId,
        @Param("variantId")
        UUID variantId
    );
}