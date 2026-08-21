package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.ProductOptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductOptionGroupRepository
    extends JpaRepository<ProductOptionGroup, UUID> {

    List<ProductOptionGroup>
        findAllByProduct_IdOrderByDisplayOrderAscNameAsc(
            UUID productId
        );

    Optional<ProductOptionGroup>
        findByIdAndProduct_Id(
            UUID id,
            UUID productId
        );
}