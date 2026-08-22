package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.ProductOptionComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductOptionComponentRepository
    extends JpaRepository<
        ProductOptionComponent,
        UUID
    > {

    List<ProductOptionComponent>
        findAllByProductOption_IdOrderByIdAsc(
            UUID productOptionId
        );

    Optional<ProductOptionComponent>
        findByIdAndProductOption_Id(
            UUID id,
            UUID productOptionId
        );
}