package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.StockLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLotRepository
    extends JpaRepository<StockLot, UUID> {

    @Query("""
        select l
        from StockLot l
        join fetch l.stockItem si
        join fetch l.stockLocation sl
        left join fetch l.supplier s
        where si.organization.id = :organizationId
          and (
                :stockLocationId is null
                or sl.id = :stockLocationId
              )
          and (
                :stockItemId is null
                or si.id = :stockItemId
              )
          and (
                :remainingOnly = false
                or l.quantityRemaining > 0
              )
        order by
            case
                when l.expiresAt is null
                then 1
                else 0
            end asc,
            l.expiresAt asc,
            l.receivedAt asc,
            l.id asc
        """)
    List<StockLot> search(
        @Param("organizationId")
        UUID organizationId,

        @Param("stockLocationId")
        UUID stockLocationId,

        @Param("stockItemId")
        UUID stockItemId,

        @Param("remainingOnly")
        boolean remainingOnly
    );

    @Query("""
        select l
        from StockLot l
        join fetch l.stockItem si
        join fetch l.stockLocation sl
        left join fetch l.supplier s
        where l.id = :lotId
          and si.organization.id = :organizationId
        """)
    Optional<StockLot> findOwnedById(
        @Param("lotId")
        UUID lotId,

        @Param("organizationId")
        UUID organizationId
    );
}