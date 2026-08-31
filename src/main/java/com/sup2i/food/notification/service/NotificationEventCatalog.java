package com.sup2i.food.notification.service;

import com.sup2i.food.notification.domain.NotificationChannel;
import com.sup2i.food.notification.domain.NotificationEventDefinition;
import com.sup2i.food.notification.domain.NotificationEventType;
import com.sup2i.food.notification.domain.NotificationPriority;
import com.sup2i.food.notification.exception.NotificationValidationException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Service
public class NotificationEventCatalog {

    private static final Map<
        NotificationEventType,
        NotificationEventDefinition
    > DEFINITIONS =
        definitions();

    public NotificationEventDefinition get(
        NotificationEventType type
    ) {

        if (type == null) {
            throw new NotificationValidationException(
                "Notification event type is required."
            );
        }

        NotificationEventDefinition definition =
            DEFINITIONS.get(
                type
            );

        if (definition == null) {
            throw new NotificationValidationException(
                "Notification event type is not supported."
            );
        }

        return definition;
    }

    public Map<
        NotificationEventType,
        NotificationEventDefinition
    > all() {

        return Map.copyOf(
            DEFINITIONS
        );
    }

    private static Map<
        NotificationEventType,
        NotificationEventDefinition
    > definitions() {

        EnumMap<
            NotificationEventType,
            NotificationEventDefinition
        > result =
            new EnumMap<>(
                NotificationEventType.class
            );

        put(
            result,
            NotificationEventType.ORDER_CREATED,
            required(
                NotificationChannel.IN_APP
            ),
            none(),
            NotificationPriority.NORMAL
        );

        put(
            result,
            NotificationEventType.PAYMENT_CONFIRMED,
            required(
                NotificationChannel.IN_APP,
                NotificationChannel.PUSH
            ),
            none(),
            NotificationPriority.HIGH
        );

        put(
            result,
            NotificationEventType.PREPARATION_STARTED,
            required(
                NotificationChannel.IN_APP
            ),
            none(),
            NotificationPriority.NORMAL
        );

        put(
            result,
            NotificationEventType.ORDER_READY,
            required(
                NotificationChannel.PUSH,
                NotificationChannel.IN_APP
            ),
            none(),
            NotificationPriority.HIGH
        );

        put(
            result,
            NotificationEventType.SLOT_SOON,
            required(
                NotificationChannel.PUSH
            ),
            none(),
            NotificationPriority.NORMAL
        );

        put(
            result,
            NotificationEventType.ORDER_EXPIRED,
            required(
                NotificationChannel.IN_APP
            ),
            none(),
            NotificationPriority.NORMAL
        );

        put(
            result,
            NotificationEventType.ORDER_CANCELLED,
            required(
                NotificationChannel.PUSH,
                NotificationChannel.IN_APP
            ),
            none(),
            NotificationPriority.HIGH
        );

        put(
            result,
            NotificationEventType.CANTEEN_MENU_PUBLISHED,
            none(),
            optional(
                NotificationChannel.PUSH
            ),
            NotificationPriority.NORMAL
        );

        put(
            result,
            NotificationEventType.CANTEEN_RESERVATION_REMINDER,
            required(
                NotificationChannel.PUSH
            ),
            none(),
            NotificationPriority.NORMAL
        );

        put(
            result,
            NotificationEventType.CANTEEN_REDEMPTION,
            required(
                NotificationChannel.IN_APP
            ),
            none(),
            NotificationPriority.LOW
        );

        put(
            result,
            NotificationEventType.SUBSCRIPTION_EXPIRING,
            required(
                NotificationChannel.PUSH
            ),
            none(),
            NotificationPriority.NORMAL
        );

        put(
            result,
            NotificationEventType.QUOTA_LOW,
            required(
                NotificationChannel.PUSH,
                NotificationChannel.IN_APP
            ),
            none(),
            NotificationPriority.NORMAL
        );

        put(
            result,
            NotificationEventType.FOOD_PASS_BLOCKED,
            required(
                NotificationChannel.PUSH,
                NotificationChannel.IN_APP
            ),
            none(),
            NotificationPriority.HIGH
        );

        put(
            result,
            NotificationEventType.LOYALTY_REWARD_AVAILABLE,
            none(),
            optional(
                NotificationChannel.PUSH
            ),
            NotificationPriority.LOW
        );

        put(
            result,
            NotificationEventType.PROMOTION,
            none(),
            optional(
                NotificationChannel.PUSH
            ),
            NotificationPriority.LOW
        );

        return Map.copyOf(
            result
        );
    }

    private static void put(
        Map<
            NotificationEventType,
            NotificationEventDefinition
        > target,
        NotificationEventType type,
        Set<NotificationChannel> requiredChannels,
        Set<NotificationChannel> optionalChannels,
        NotificationPriority priority
    ) {

        NotificationEventDefinition previous =
            target.put(
                type,
                new NotificationEventDefinition(
                    type,
                    requiredChannels,
                    optionalChannels,
                    priority
                )
            );

        if (previous != null) {
            throw new IllegalStateException(
                "Duplicate notification event definition: " +
                type
            );
        }
    }

    private static Set<NotificationChannel> required(
        NotificationChannel first,
        NotificationChannel... others
    ) {

        return channels(
            first,
            others
        );
    }

    private static Set<NotificationChannel> optional(
        NotificationChannel first,
        NotificationChannel... others
    ) {

        return channels(
            first,
            others
        );
    }

    private static Set<NotificationChannel> channels(
        NotificationChannel first,
        NotificationChannel... others
    ) {

        if (others.length == 0) {
            return Set.of(
                first
            );
        }

        java.util.EnumSet<NotificationChannel> channels =
            java.util.EnumSet.of(
                first,
                others
            );

        return Set.copyOf(
            channels
        );
    }

    private static Set<NotificationChannel> none() {

        return Set.of();
    }
}