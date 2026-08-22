package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.StockBalance;
import com.sup2i.food.inventory.domain.StockBalanceId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockBalanceRepository
    extends JpaRepository<
        StockBalance,
        StockBalanceId
    > {

    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            INSERT INTO stock_balances (
                stock_item_id,
                stock_location_id,
                physical_quantity,
                reserved_quantity,
                updated_at
            )
            VALUES (
                :stockItemId,
                :stockLocationId,
                0,
                0,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (
                stock_item_id,
                stock_location_id
            )
            DO NOTHING
            """,
        nativeQuery = true
    )
    int ensureExists(
        @Param("stockItemId")
        UUID stockItemId,

        @Param("stockLocationId")
        UUID stockLocationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select b
        from StockBalance b
        where b.id = :id
        """)
    Optional<StockBalance> findLockedById(
        @Param("id")
        StockBalanceId id
    );

    @Query("""
        select b
        from StockBalance b
        join fetch b.stockItem si
        join fetch b.stockLocation sl
        where si.organization.id = :organizationId
        order by sl.name asc, si.id asc
        """)
    List<StockBalance> findAllForOrganization(
        @Param("organizationId")
        UUID organizationId
    );

    @Query("""
        select b
        from StockBalance b
        join fetch b.stockItem si
        join fetch b.stockLocation sl
        where si.organization.id = :organizationId
          and sl.id = :stockLocationId
        order by si.id asc
        """)
    List<StockBalance> findAllForOrganizationAndLocation(
        @Param("organizationId")
        UUID organizationId,

        @Param("stockLocationId")
        UUID stockLocationId
    );

    @Query("""
        select b
        from StockBalance b
        join fetch b.stockItem item
        join fetch b.stockLocation location
        where item.organization.id = :organizationId
        order by item.id asc, location.id asc
        """)
    java.util.List<StockBalance>
        findAllForAlertReconciliation(
            @Param("organizationId")
            UUID organizationId
        );
}