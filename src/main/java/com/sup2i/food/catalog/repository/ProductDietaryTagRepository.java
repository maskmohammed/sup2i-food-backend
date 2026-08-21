package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.ProductDietaryTag;
import com.sup2i.food.catalog.domain.ProductDietaryTagId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductDietaryTagRepository
    extends JpaRepository<
        ProductDietaryTag,
        ProductDietaryTagId
    > {

    List<ProductDietaryTag>
        findAllByProduct_IdAndDietaryTag_ActiveTrueOrderByDietaryTag_NameAsc(
            UUID productId
        );

    void deleteAllByProduct_Id(
        UUID productId
    );
}