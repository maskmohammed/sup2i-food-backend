package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.Allergen;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AllergenRepository
    extends JpaRepository<Allergen, UUID> {

    List<Allergen>
        findAllByOrganization_IdAndActiveTrueOrderByNameAsc(
            UUID organizationId
        );

    List<Allergen>
        findAllByIdInAndOrganization_IdAndActiveTrue(
            Collection<UUID> ids,
            UUID organizationId
        );
}