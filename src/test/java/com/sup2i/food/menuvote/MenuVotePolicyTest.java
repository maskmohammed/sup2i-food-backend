package com.sup2i.food.menuvote;

import com.sup2i.food.menuvote.domain.MenuVoteStatus;
import com.sup2i.food.menuvote.service.MenuVotePolicy;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MenuVotePolicyTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse("2026-08-28T12:00:00Z");

    @Test
    @DisplayName("Open session with future deadline is open")
    void openSessionWithFutureDeadlineIsOpen() {
        assertThat(
            MenuVotePolicy.isOpen(
                MenuVoteStatus.OPEN,
                NOW,
                NOW.plusDays(3)
            )
        )
            .isTrue();
    }

    @Test
    @DisplayName("Open session with passed deadline is closed")
    void openSessionWithPassedDeadlineIsClosed() {
        assertThat(
            MenuVotePolicy.isOpen(
                MenuVoteStatus.OPEN,
                NOW,
                NOW.minusHours(1)
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Closed session is never open")
    void closedSessionIsNeverOpen() {
        assertThat(
            MenuVotePolicy.isOpen(
                MenuVoteStatus.CLOSED,
                NOW,
                NOW.plusDays(3)
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Null status is not open")
    void nullStatusIsNotOpen() {
        assertThat(
            MenuVotePolicy.isOpen(
                null,
                NOW,
                NOW.plusDays(3)
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Null deadline is not open")
    void nullDeadlineIsNotOpen() {
        assertThat(
            MenuVotePolicy.isOpen(
                MenuVoteStatus.OPEN,
                NOW,
                null
            )
        )
            .isFalse();
    }
}