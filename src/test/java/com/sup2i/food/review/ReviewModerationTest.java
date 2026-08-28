package com.sup2i.food.review;

import com.sup2i.food.review.domain.ModerationStatus;
import com.sup2i.food.review.exception.ReviewValidationException;
import com.sup2i.food.review.service.ReviewModeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewModerationTest {

    @Test
    @DisplayName("PENDING review can be approved")
    void pendingCanBeApproved() {
        ModerationStatus result =
            ReviewModeration.moderate(
                ModerationStatus.PENDING,
                ModerationStatus.APPROVED
            );

        assertThat(result)
            .isEqualTo(ModerationStatus.APPROVED);
    }

    @Test
    @DisplayName("PENDING review can be rejected")
    void pendingCanBeRejected() {
        ModerationStatus result =
            ReviewModeration.moderate(
                ModerationStatus.PENDING,
                ModerationStatus.REJECTED
            );

        assertThat(result)
            .isEqualTo(ModerationStatus.REJECTED);
    }

    @Test
    @DisplayName("Final state can be overridden to the other final state")
    void approvedCanBeOverridden() {
        ModerationStatus result =
            ReviewModeration.moderate(
                ModerationStatus.APPROVED,
                ModerationStatus.REJECTED
            );

        assertThat(result)
            .isEqualTo(ModerationStatus.REJECTED);
    }

    @ParameterizedTest
    @EnumSource(
        value = ModerationStatus.class,
        names = {"APPROVED", "REJECTED"}
    )
    @DisplayName("Target must be approved or rejected, never pending")
    void targetCannotBePending(ModerationStatus current) {
        assertThatThrownBy(() ->
            ReviewModeration.moderate(
                current,
                ModerationStatus.PENDING
            )
        )
            .isInstanceOf(ReviewValidationException.class)
            .hasMessageContaining("approved or rejected");
    }

    @Test
    @DisplayName("Null target is rejected")
    void nullTargetIsRejected() {
        assertThatThrownBy(() ->
            ReviewModeration.moderate(
                ModerationStatus.PENDING,
                null
            )
        )
            .isInstanceOf(ReviewValidationException.class);
    }

    @Test
    @DisplayName("Null current status is rejected")
    void nullCurrentIsRejected() {
        assertThatThrownBy(() ->
            ReviewModeration.moderate(
                null,
                ModerationStatus.APPROVED
            )
        )
            .isInstanceOf(ReviewValidationException.class)
            .hasMessageContaining("no moderation status");
    }
}