package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.StockReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StockReceiptLineRepository
    extends JpaRepository<
        StockReceiptLine,
        UUID
    > {

    @Query("""
        select l
        from StockReceiptLine l
        join fetch l.stockItem si
        join fetch l.stockReceipt receipt
        left join fetch l.generatedLot lot
        left join fetch l.inventoryMovement movement
        where receipt.id = :receiptId
        order by l.id asc
        """)
    List<StockReceiptLine>
        findAllForReceipt(
            @Param("receiptId")
            UUID receiptId
        );

    List<StockReceiptLine>
        findAllByIdIn(
            Collection<UUID> ids
        );
}