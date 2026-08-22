package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockItemRepository
    extends JpaRepository<StockItem, UUID> {

    Optional<StockItem>
        findByIdAndOrganization_Id(
            UUID id,
            UUID organizationId
        );

    List<StockItem>
        findAllByOrganization_IdOrderByCreatedAtDesc(
            UUID organizationId
        );

    boolean existsByOrganization_IdAndProduct_Id(
        UUID organizationId,
        UUID productId
    );

    boolean existsByOrganization_IdAndVariant_Id(
        UUID organizationId,
        UUID variantId
    );

    boolean existsByOrganization_IdAndIngredient_Id(
        UUID organizationId,
        UUID ingredientId
    );
}