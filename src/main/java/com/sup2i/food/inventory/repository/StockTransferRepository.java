package com.sup2i.food.inventory.repository;

import com.sup2i.food.inventory.domain.StockTransfer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockTransferRepository
    extends JpaRepository<
        StockTransfer,
        UUID
    > {

    @Query("""
        select t
        from StockTransfer t
        join fetch t.sourceStockLocation source
        join fetch source.location sourceLocation
        join fetch sourceLocation.campus sourceCampus
        join fetch t.destinationStockLocation destination
        join fetch destination.location destinationLocation
        join fetch destinationLocation.campus destinationCampus
        join fetch t.requestedBy requester
        left join fetch t.approvedBy approver
        left join fetch t.dispatchedBy dispatcher
        left join fetch t.receivedBy receiver
        where t.id = :transferId
          and sourceCampus.organization.id = :organizationId
          and destinationCampus.organization.id = :organizationId
        """)
    Optional<StockTransfer>
        findOwnedById(
            @Param("transferId")
            UUID transferId,

            @Param("organizationId")
            UUID organizationId
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select t
        from StockTransfer t
        join fetch t.sourceStockLocation source
        join fetch source.location sourceLocation
        join fetch sourceLocation.campus sourceCampus
        join fetch t.destinationStockLocation destination
        join fetch destination.location destinationLocation
        join fetch destinationLocation.campus destinationCampus
        where t.id = :transferId
          and sourceCampus.organization.id = :organizationId
          and destinationCampus.organization.id = :organizationId
        """)
    Optional<StockTransfer>
        findOwnedByIdForUpdate(
            @Param("transferId")
            UUID transferId,

            @Param("organizationId")
            UUID organizationId
        );
}