package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.InventorySession;
import com.sup2i.food.inventory.domain.InventorySessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface InventorySessionRepository
    extends JpaRepository<
        InventorySession,
        UUID
    > {

    @Query("""
        select s
        from InventorySession s
        join fetch s.stockLocation sl
        join fetch sl.location location
        join fetch location.campus campus
        join fetch s.startedBy starter
        left join fetch s.completedBy completer
        left join fetch s.appliedBy applier
        where s.id = :sessionId
          and campus.organization.id = :organizationId
        """)
    Optional<InventorySession>
        findOwnedById(
            @Param("sessionId")
            UUID sessionId,

            @Param("organizationId")
            UUID organizationId
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select s
        from InventorySession s
        join fetch s.stockLocation sl
        join fetch sl.location location
        join fetch location.campus campus
        where s.id = :sessionId
          and campus.organization.id = :organizationId
        """)
    Optional<InventorySession>
        findOwnedByIdForUpdate(
            @Param("sessionId")
            UUID sessionId,

            @Param("organizationId")
            UUID organizationId
        );

    boolean existsByStockLocation_IdAndStatusIn(
        UUID stockLocationId,
        Collection<InventorySessionStatus> statuses
    );
}