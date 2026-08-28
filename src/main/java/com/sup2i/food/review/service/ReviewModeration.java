package com.sup2i.food.review.service;

import com.sup2i.food.review.domain.ModerationStatus;
import com.sup2i.food.review.exception.ReviewValidationException;

import java.util.Set;

/**
 * Pure moderation state machine, kept out of the entity so it can be unit
 * tested without a database.
 *
 * <p>Rules :
 * <ul>
 *   <li>a review is created PENDING and can only move to APPROVED or REJECTED;</li>
 *   <li>a PENDING review cannot be moderated to PENDING;</li>
 *   <li>an APPROVED/REJECTED review may be re-moderated to the other final
 *       state (override), but never back to PENDING;</li>
 *   <li>the final state must be one of APPROVED or REJECTED.</li>
 * </ul>
 */
public final class ReviewModeration {

    private static final Set<ModerationStatus>
        FINAL_STATES =
            Set.of(
                ModerationStatus.APPROVED,
                ModerationStatus.REJECTED
            );

    private ReviewModeration() {
    }

    public static ModerationStatus moderate(
        ModerationStatus current,
        ModerationStatus target
    ) {
        if (target == null) {
            throw new ReviewValidationException(
                "Moderation status is required."
            );
        }

        if (
            target == ModerationStatus.PENDING
        ) {
            throw new ReviewValidationException(
                "A review can only be approved or rejected during moderation."
            );
        }

        if (
            !FINAL_STATES.contains(target)
        ) {
            throw new ReviewValidationException(
                "Unknown moderation status " + target + "."
            );
        }

        if (
            current == null
        ) {
            throw new ReviewValidationException(
                "Review has no moderation status."
            );
        }

        return target;
    }
}