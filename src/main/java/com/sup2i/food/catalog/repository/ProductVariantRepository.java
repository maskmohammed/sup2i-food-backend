package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository
    extends JpaRepository<ProductVariant, UUID> {

    List<ProductVariant>
        findAllByProduct_IdAndActiveTrueOrderByDisplayOrderAscNameAsc(
            UUID productId
        );

    Optional<ProductVariant>
        findByIdAndProduct_Id(
            UUID id,
            UUID productId
        );
}