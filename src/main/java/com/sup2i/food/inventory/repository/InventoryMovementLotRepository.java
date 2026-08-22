package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.InventoryMovementLot;
import com.sup2i.food.inventory.domain.InventoryMovementLotId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementLotRepository
    extends JpaRepository<
        InventoryMovementLot,
        InventoryMovementLotId
    > {
}