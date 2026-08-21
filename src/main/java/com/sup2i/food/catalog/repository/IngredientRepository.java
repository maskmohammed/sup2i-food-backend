package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngredientRepository
    extends JpaRepository<Ingredient, UUID> {

    boolean existsByOrganization_IdAndCode(
        UUID organizationId,
        String code
    );

    Optional<Ingredient>
        findByIdAndOrganization_Id(
            UUID id,
            UUID organizationId
        );

    List<Ingredient>
        findAllByOrganization_IdAndActiveTrueOrderByNameAsc(
            UUID organizationId
        );

    List<Ingredient>
        findAllByIdInAndOrganization_Id(
            Collection<UUID> ids,
            UUID organizationId
        );
}