package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.InventoryMovementLot;
import com.sup2i.food.inventory.domain.InventoryMovementLotId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InventoryMovementLotRepository
    extends JpaRepository<
        InventoryMovementLot,
        InventoryMovementLotId
    > {

    @Query("""
        select link
        from InventoryMovementLot link
        join fetch link.stockLot lot
        left join fetch lot.supplier supplier
        where link.movement.id = :movementId
        order by
            case
                when lot.expiresAt is null
                then 1
                else 0
            end asc,
            lot.expiresAt asc,
            lot.receivedAt asc,
            lot.id asc
        """)
    List<InventoryMovementLot>
        findAllForMovement(
            @Param("movementId")
            UUID movementId
        );
}