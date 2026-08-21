package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.DietaryTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DietaryTagRepository
    extends JpaRepository<DietaryTag, UUID> {

    List<DietaryTag>
        findAllByOrganization_IdAndActiveTrueOrderByNameAsc(
            UUID organizationId
        );

    List<DietaryTag>
        findAllByIdInAndOrganization_IdAndActiveTrue(
            Collection<UUID> ids,
            UUID organizationId
        );
}