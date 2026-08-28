package com.sup2i.food.purchase.repository;

import com.sup2i.food.purchase.domain.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository
    extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder>
        findByIdAndOrganization_Id(
            UUID id,
            UUID organizationId
        );

    List<PurchaseOrder>
        findAllByOrganization_IdOrderByCreatedAtDesc(
            UUID organizationId
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select po
        from PurchaseOrder po
        join fetch po.supplier supplier
        where po.id = :purchaseOrderId
          and po.organization.id = :organizationId
        """)
    Optional<PurchaseOrder>
        findOwnedByIdForUpdate(
            @Param("purchaseOrderId")
            UUID purchaseOrderId,
            @Param("organizationId")
            UUID organizationId
        );
}