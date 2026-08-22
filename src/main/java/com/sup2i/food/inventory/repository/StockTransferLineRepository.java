package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.StockTransferLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StockTransferLineRepository
    extends JpaRepository<
        StockTransferLine,
        UUID
    > {

    @Query("""
        select l
        from StockTransferLine l
        join fetch l.stockItem item
        left join fetch item.product product
        left join fetch item.variant variant
        left join fetch variant.product variantProduct
        left join fetch item.ingredient ingredient
        left join fetch l.transferOutMovement outMovement
        left join fetch l.transferInMovement inMovement
        where l.stockTransfer.id = :transferId
        order by item.id asc
        """)
    List<StockTransferLine>
        findAllForTransfer(
            @Param("transferId")
            UUID transferId
        );
}