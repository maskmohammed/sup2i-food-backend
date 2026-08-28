package com.sup2i.food.purchase.repository;

import com.sup2i.food.purchase.domain.PurchaseOrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderHistoryRepository
    extends JpaRepository<PurchaseOrderHistory, UUID> {

    List<PurchaseOrderHistory>
        findAllByPurchaseOrder_IdOrderByOccurredAtDesc(
            UUID purchaseOrderId
        );
}