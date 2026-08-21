package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.ProductAllergen;
import com.sup2i.food.catalog.domain.ProductAllergenId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductAllergenRepository
    extends JpaRepository<
        ProductAllergen,
        ProductAllergenId
    > {

    List<ProductAllergen>
        findAllByProduct_IdAndAllergen_ActiveTrueOrderByAllergen_NameAsc(
            UUID productId
        );

    void deleteAllByProduct_Id(
        UUID productId
    );
}