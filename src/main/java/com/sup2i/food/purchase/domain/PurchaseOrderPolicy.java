package com.sup2i.food.purchase.domain;

/**
 * Pure state-machine for purchase orders (validated through unit tests).
 *
 * Lifecycle : DRAFT -> SENT -> CONFIRMED -> PARTIALLY_RECEIVED / RECEIVED.
 * Cancellation is only allowed while the order has not been confirmed.
 */
public final class PurchaseOrderPolicy {

    private PurchaseOrderPolicy() {
    }

    public static boolean canEdit(
        PurchaseOrderStatus status
    ) {
        return status
            == PurchaseOrderStatus.DRAFT;
    }

    public static boolean canSend(
        PurchaseOrderStatus status
    ) {
        return status
            == PurchaseOrderStatus.DRAFT;
    }

    public static boolean canConfirm(
        PurchaseOrderStatus status
    ) {
        return status
            == PurchaseOrderStatus.SENT;
    }

    public static boolean canReceive(
        PurchaseOrderStatus status
    ) {
        return status
            == PurchaseOrderStatus.CONFIRMED
            || status
                == PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    public static boolean canCancel(
        PurchaseOrderStatus status
    ) {
        return status
            == PurchaseOrderStatus.DRAFT
            || status
                == PurchaseOrderStatus.SENT;
    }

    public static boolean isTerminal(
        PurchaseOrderStatus status
    ) {
        return status
            == PurchaseOrderStatus.RECEIVED
            || status
                == PurchaseOrderStatus.CANCELLED;
    }
}