package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.StockAlert;
import com.sup2i.food.inventory.domain.StockAlertStatus;
import com.sup2i.food.inventory.domain.StockAlertType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockAlertRepository
    extends JpaRepository<
        StockAlert,
        UUID
    > {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a
        from StockAlert a
        join a.stockItem item
        where item.organization.id = :organizationId
          and a.status in :statuses
          and a.alertType in :types
        order by a.detectedAt asc, a.id asc
        """)
    List<StockAlert> findManagedActiveForUpdate(
        @Param("organizationId")
        UUID organizationId,

        @Param("statuses")
        Collection<StockAlertStatus> statuses,

        @Param("types")
        Collection<StockAlertType> types
    );

    @Query("""
        select a
        from StockAlert a
        join fetch a.stockItem item
        left join fetch a.stockLocation location
        left join fetch a.lot lot
        left join fetch a.acknowledgedBy acknowledged
        where item.organization.id = :organizationId
          and (
                :status is null
                or a.status = :status
              )
          and (
                :alertType is null
                or a.alertType = :alertType
              )
          and (
                :stockLocationId is null
                or location.id = :stockLocationId
              )
          and (
                :stockItemId is null
                or item.id = :stockItemId
              )
        order by a.detectedAt desc, a.id asc
        """)
    List<StockAlert> search(
        @Param("organizationId")
        UUID organizationId,

        @Param("status")
        StockAlertStatus status,

        @Param("alertType")
        StockAlertType alertType,

        @Param("stockLocationId")
        UUID stockLocationId,

        @Param("stockItemId")
        UUID stockItemId
    );

    @Query("""
        select a
        from StockAlert a
        join fetch a.stockItem item
        left join fetch a.stockLocation location
        left join fetch a.lot lot
        left join fetch a.acknowledgedBy acknowledged
        where a.id = :alertId
          and item.organization.id = :organizationId
        """)
    Optional<StockAlert> findOwnedById(
        @Param("alertId")
        UUID alertId,

        @Param("organizationId")
        UUID organizationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a
        from StockAlert a
        join fetch a.stockItem item
        left join fetch a.stockLocation location
        left join fetch a.lot lot
        left join fetch a.acknowledgedBy acknowledged
        where a.id = :alertId
          and item.organization.id = :organizationId
        """)
    Optional<StockAlert> findOwnedByIdForUpdate(
        @Param("alertId")
        UUID alertId,

        @Param("organizationId")
        UUID organizationId
    );
}