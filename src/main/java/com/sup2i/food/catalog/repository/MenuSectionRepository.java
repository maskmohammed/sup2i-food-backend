package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.MenuSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuSectionRepository
    extends JpaRepository<MenuSection, UUID> {

    List<MenuSection>
        findAllByMenu_IdAndActiveTrueOrderByDisplayOrderAscNameAsc(
            UUID menuId
        );

    Optional<MenuSection>
        findByIdAndMenu_Id(
            UUID id,
            UUID menuId
        );
}