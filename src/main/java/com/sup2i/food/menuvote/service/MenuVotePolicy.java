package com.sup2i.food.menuvote.service;

import com.sup2i.food.menuvote.domain.MenuVoteStatus;

import java.time.OffsetDateTime;

/**
 * Pure vote policy, unit tested without a database.
 */
public final class MenuVotePolicy {

    private MenuVotePolicy() {
    }

    public static boolean isOpen(
        MenuVoteStatus status,
        OffsetDateTime toCheck,
        OffsetDateTime deadline
    ) {
        if (status == null) {
            return false;
        }

        if (status != MenuVoteStatus.OPEN) {
            return false;
        }

        if (
            deadline == null
                || toCheck == null
        ) {
            return false;
        }

        return deadline.isAfter(toCheck);
    }
}