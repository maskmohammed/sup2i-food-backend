package com.sup2i.food.purchase;

import com.sup2i.food.purchase.domain.PurchaseOrderPolicy;
import com.sup2i.food.purchase.domain.PurchaseOrderStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseOrderPolicyTest {

    @Test
    @DisplayName("Only draft orders can be edited")
    void onlyDraftOrdersCanBeEdited() {
        for (
            PurchaseOrderStatus status
            : PurchaseOrderStatus.values()
        ) {
            assertThat(
                PurchaseOrderPolicy.canEdit(status)
            )
                .isEqualTo(
                    status
                        == PurchaseOrderStatus.DRAFT
                );
        }
    }

    @Test
    @DisplayName("Only draft orders can be sent")
    void onlyDraftOrdersCanBeSent() {
        assertThat(
            PurchaseOrderPolicy.canSend(
                PurchaseOrderStatus.DRAFT
            )
        )
            .isTrue();

        assertThat(
            PurchaseOrderPolicy.canSend(
                PurchaseOrderStatus.SENT
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Only sent orders can be confirmed")
    void onlySentOrdersCanBeConfirmed() {
        assertThat(
            PurchaseOrderPolicy.canConfirm(
                PurchaseOrderStatus.SENT
            )
        )
            .isTrue();

        assertThat(
            PurchaseOrderPolicy.canConfirm(
                PurchaseOrderStatus.DRAFT
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Confirmed and partially received orders accept receiving")
    void confirmedAndPartiallyReceivedAcceptReceiving() {
        assertThat(
            PurchaseOrderPolicy.canReceive(
                PurchaseOrderStatus.CONFIRMED
            )
        )
            .isTrue();

        assertThat(
            PurchaseOrderPolicy.canReceive(
                PurchaseOrderStatus.PARTIALLY_RECEIVED
            )
        )
            .isTrue();

        assertThat(
            PurchaseOrderPolicy.canReceive(
                PurchaseOrderStatus.SENT
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Draft and sent orders can be cancelled, confirmed cannot")
    void cancellationAllowedBeforeConfirmation() {
        assertThat(
            PurchaseOrderPolicy.canCancel(
                PurchaseOrderStatus.DRAFT
            )
        )
            .isTrue();

        assertThat(
            PurchaseOrderPolicy.canCancel(
                PurchaseOrderStatus.SENT
            )
        )
            .isTrue();

        assertThat(
            PurchaseOrderPolicy.canCancel(
                PurchaseOrderStatus.CONFIRMED
            )
        )
            .isFalse();

        assertThat(
            PurchaseOrderPolicy.canCancel(
                PurchaseOrderStatus.RECEIVED
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Received and cancelled are terminal")
    void receivedAndCancelledAreTerminal() {
        assertThat(
            PurchaseOrderPolicy.isTerminal(
                PurchaseOrderStatus.RECEIVED
            )
        )
            .isTrue();

        assertThat(
            PurchaseOrderPolicy.isTerminal(
                PurchaseOrderStatus.CANCELLED
            )
        )
            .isTrue();

        assertThat(
            PurchaseOrderPolicy.isTerminal(
                PurchaseOrderStatus.DRAFT
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Null status is never allowed")
    void nullStatusIsNeverAllowed() {
        assertThat(
            PurchaseOrderPolicy.canEdit(null)
        )
            .isFalse();

        assertThat(
            PurchaseOrderPolicy.canReceive(null)
        )
            .isFalse();

        assertThat(
            PurchaseOrderPolicy.canCancel(null)
        )
            .isFalse();
    }
}