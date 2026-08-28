package com.sup2i.food.survey;

import com.sup2i.food.survey.domain.SurveyTarget;
import com.sup2i.food.survey.service.SurveyTargetMatcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SurveyTargetMatcherTest {

    @Test
    @DisplayName("ALL always matches")
    void allMatchesEveryStudent() {
        assertThat(
            SurveyTargetMatcher.matches(
                SurveyTarget.ALL,
                false,
                false
            )
        )
            .isTrue();
    }

    @Test
    @DisplayName("ORDERED matches a student with a delivered order")
    void orderedMatchesWithDeliveredOrder() {
        assertThat(
            SurveyTargetMatcher.matches(
                SurveyTarget.ORDERED,
                true,
                false
            )
        )
            .isTrue();
    }

    @Test
    @DisplayName("ORDERED excludes a student without delivered order")
    void orderedExcludesWithoutDeliveredOrder() {
        assertThat(
            SurveyTargetMatcher.matches(
                SurveyTarget.ORDERED,
                false,
                true
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("SUBSCRIBED matches an active subscriber")
    void subscribedMatchesActiveSubscriber() {
        assertThat(
            SurveyTargetMatcher.matches(
                SurveyTarget.SUBSCRIBED,
                false,
                true
            )
        )
            .isTrue();
    }

    @Test
    @DisplayName("SUBSCRIBED excludes a non-subscriber")
    void subscribedExcludesNonSubscriber() {
        assertThat(
            SurveyTargetMatcher.matches(
                SurveyTarget.SUBSCRIBED,
                true,
                false
            )
        )
            .isFalse();
    }

    @Test
    @DisplayName("Null target never matches")
    void nullTargetNeverMatches() {
        assertThat(
            SurveyTargetMatcher.matches(
                null,
                true,
                true
            )
        )
            .isFalse();
    }
}