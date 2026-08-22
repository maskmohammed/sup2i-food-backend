package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.InventoryCountLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryCountLineRepository
    extends JpaRepository<
        InventoryCountLine,
        UUID
    > {

    Optional<InventoryCountLine>
        findByInventorySession_IdAndStockItem_Id(
            UUID inventorySessionId,
            UUID stockItemId
        );

    @Query("""
        select l
        from InventoryCountLine l
        join fetch l.stockItem si
        left join fetch l.countedBy countedBy
        left join fetch l.adjustmentMovement movement
        where l.inventorySession.id = :sessionId
        order by si.id asc
        """)
    List<InventoryCountLine>
        findAllForSession(
            @Param("sessionId")
            UUID sessionId
        );

    long countByInventorySession_Id(
        UUID inventorySessionId
    );
}