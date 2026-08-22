package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.InventoryMovement;
import com.sup2i.food.inventory.domain.InventoryMovementType;
import org.springframework.data.jpa.repository.JpaRepository;

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
}