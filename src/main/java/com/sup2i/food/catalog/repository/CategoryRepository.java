package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository
    extends JpaRepository<Category, UUID> {

    List<Category>
        findAllByOrganization_IdAndActiveTrueOrderByDisplayOrderAscNameAsc(
            UUID organizationId
        );

    Optional<Category>
        findByIdAndOrganization_Id(
            UUID id,
            UUID organizationId
        );

    boolean
        existsByOrganization_IdAndSlugIgnoreCase(
            UUID organizationId,
            String slug
        );
}