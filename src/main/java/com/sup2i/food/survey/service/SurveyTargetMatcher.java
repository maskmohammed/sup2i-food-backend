package com.sup2i.food.survey.service;

import com.sup2i.food.survey.domain.SurveyTarget;

/**
 * Pure targeting predicate for surveys, unit tested without a database.
 *
 * <p>A student can see / respond to a survey when :
 * <ul>
 *   <li>{@code ALL} : every student of the organization;</li>
 *   <li>{@code ORDERED} : the student has at least one delivered order;</li>
 *   <li>{@code SUBSCRIBED} : the student has an active subscription.</li>
 * </ul>
 */
public final class SurveyTargetMatcher {

    private SurveyTargetMatcher() {
    }

    public static boolean matches(
        SurveyTarget target,
        boolean hasDeliveredOrder,
        boolean hasActiveSubscription
    ) {
        if (target == null) {
            return false;
        }

        return switch (target) {
            case ALL -> true;
            case ORDERED -> hasDeliveredOrder;
            case SUBSCRIBED -> hasActiveSubscription;
        };
    }
}