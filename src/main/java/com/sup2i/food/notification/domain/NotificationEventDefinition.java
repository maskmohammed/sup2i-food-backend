package com.sup2i.food.notification.domain;

import java.util.Set;

/**
 * Normative channel and priority definition for one functional
 * notification event.
 *
 * requiredChannels are channels explicitly required by the
 * functional specification.
 *
 * optionalChannels are channels explicitly defined as optional
 * or preference-driven.
 *
 * Preference-category mapping is deliberately not encoded here.
 */
public record NotificationEventDefinition(
    NotificationEventType type,
    Set<NotificationChannel> requiredChannels,
    Set<NotificationChannel> optionalChannels,
    NotificationPriority priority
) {

    public NotificationEventDefinition {

        requiredChannels =
            Set.copyOf(
                requiredChannels
            );

        optionalChannels =
            Set.copyOf(
                optionalChannels
            );
    }
}