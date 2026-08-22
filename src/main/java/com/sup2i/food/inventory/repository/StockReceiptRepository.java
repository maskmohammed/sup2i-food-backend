package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.StockReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockReceiptRepository
    extends JpaRepository<StockReceipt, UUID> {

    @Query("""
        select r
        from StockReceipt r
        join fetch r.stockLocation sl
        join fetch sl.location loc
        join fetch loc.campus campus
        left join fetch r.supplier supplier
        join fetch r.receivedBy receiver
        where r.id = :receiptId
          and campus.organization.id = :organizationId
        """)
    Optional<StockReceipt> findOwnedById(
        @Param("receiptId")
        UUID receiptId,

        @Param("organizationId")
        UUID organizationId
    );
}