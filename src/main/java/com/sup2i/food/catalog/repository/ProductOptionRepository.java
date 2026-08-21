package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductOptionRepository
    extends JpaRepository<ProductOption, UUID> {

    List<ProductOption>
        findAllByOptionGroup_IdInAndActiveTrueOrderByOptionGroup_IdAscDisplayOrderAscNameAsc(
            Collection<UUID> groupIds
        );
}