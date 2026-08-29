package com.sup2i.food.kitchen.service.readiness;

import java.util.Objects;

public record KitchenOrderReadinessDecision(
    KitchenOrderReadiness readiness,
    int totalTickets,
    int readyTickets,
    int waitingTickets,
    int cancelledTickets
) {

    public KitchenOrderReadinessDecision {

        Objects.requireNonNull(
            readiness,
            "readiness"
        );

        if (totalTickets <= 0) {
            throw new IllegalArgumentException(
                "Kitchen readiness requires at least one ticket."
            );
        }

        if (
            readyTickets < 0
            || waitingTickets < 0
            || cancelledTickets < 0
        ) {
            throw new IllegalArgumentException(
                "Kitchen readiness counts cannot be negative."
            );
        }

        int classifiedTickets =
            readyTickets
                + waitingTickets
                + cancelledTickets;

        if (
            classifiedTickets
                != totalTickets
        ) {
            throw new IllegalArgumentException(
                "Kitchen readiness counts must classify every ticket."
            );
        }

        if (
            readiness
                == KitchenOrderReadiness.ORDER_READY
            && readyTickets
                != totalTickets
        ) {
            throw new IllegalArgumentException(
                "ORDER_READY requires every ticket to be READY."
            );
        }

        if (
            readiness
                == KitchenOrderReadiness.BLOCKED_BY_CANCELLATION
            && cancelledTickets
                == 0
        ) {
            throw new IllegalArgumentException(
                "Cancellation-blocked readiness requires a cancelled ticket."
            );
        }

        if (
            readiness
                == KitchenOrderReadiness.WAITING
            && waitingTickets
                == 0
        ) {
            throw new IllegalArgumentException(
                "WAITING readiness requires at least one waiting ticket."
            );
        }
    }

    public boolean orderReady() {
        return readiness
            == KitchenOrderReadiness.ORDER_READY;
    }

    public boolean blockedByCancellation() {
        return readiness
            == KitchenOrderReadiness.BLOCKED_BY_CANCELLATION;
    }
}
