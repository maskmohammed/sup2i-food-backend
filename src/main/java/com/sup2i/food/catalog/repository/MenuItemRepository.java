package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MenuItemRepository
    extends JpaRepository<MenuItem, UUID> {

    List<MenuItem>
        findAllByMenuSection_IdInAndActiveTrueOrderByMenuSection_IdAscDisplayOrderAscProduct_NameAsc(
            Collection<UUID> sectionIds
        );
}