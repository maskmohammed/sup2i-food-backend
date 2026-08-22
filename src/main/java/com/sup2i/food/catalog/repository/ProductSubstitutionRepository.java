package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.ProductSubstitution;
import com.sup2i.food.catalog.domain.ProductSubstitutionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductSubstitutionRepository
    extends JpaRepository<
        ProductSubstitution,
        ProductSubstitutionId
    > {

    Optional<ProductSubstitution>
        findByProduct_IdAndSubstituteProduct_Id(
            UUID productId,
            UUID substituteProductId
        );

    List<ProductSubstitution>
        findAllByProduct_IdOrderByPriorityAscSubstituteProduct_NameAsc(
            UUID productId
        );
}