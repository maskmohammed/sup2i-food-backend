package com.sup2i.food.purchase.repository;

import com.sup2i.food.purchase.domain.PurchaseOrderReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderReceiptRepository
    extends JpaRepository<PurchaseOrderReceipt, UUID> {

    List<PurchaseOrderReceipt>
        findAllByPurchaseOrder_IdOrderByReceivedAtDesc(
            UUID purchaseOrderId
        );
}