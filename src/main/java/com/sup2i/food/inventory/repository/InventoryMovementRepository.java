package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.InventoryMovement;
import com.sup2i.food.inventory.domain.InventoryMovementType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryMovementRepository
    extends JpaRepository<InventoryMovement, UUID> {

    Optional<InventoryMovement>
        findByStockItem_IdAndStockLocation_IdAndMovementTypeAndReferenceTypeAndReferenceId(
            UUID stockItemId,
            UUID stockLocationId,
            InventoryMovementType movementType,
            String referenceType,
            UUID referenceId
        );

    @Query("""
        select m.unitCost
        from InventoryMovement m
        where m.stockItem.id = :stockItemId
          and m.unitCost is not null
        order by m.createdAt desc
        """)
    List<BigDecimal> findRecentUnitCosts(
        @Param("stockItemId")
        UUID stockItemId,
        Pageable pageable
    );
}