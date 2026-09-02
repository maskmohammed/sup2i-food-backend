package com.sup2i.food.canteen.service;

import com.sup2i.food.subscription.service.SubscriptionLifecycleService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile("!test")
public class CanteenSubscriptionLifecycleScheduler {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            CanteenSubscriptionLifecycleScheduler.class
        );

    private static final int BATCH_SIZE =
        500;

    private final JdbcTemplate jdbcTemplate;

    private final SubscriptionLifecycleService
        subscriptionLifecycleService;

    private final CanteenLifecycleService
        canteenLifecycleService;

    public CanteenSubscriptionLifecycleScheduler(
        JdbcTemplate jdbcTemplate,
        SubscriptionLifecycleService subscriptionLifecycleService,
        CanteenLifecycleService canteenLifecycleService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.subscriptionLifecycleService =
            subscriptionLifecycleService;

        this.canteenLifecycleService =
            canteenLifecycleService;
    }

    @Scheduled(
        fixedDelayString =
            "${sup2i.lifecycle.canteen-subscription-sweep-ms:60000}",
        initialDelayString =
            "${sup2i.lifecycle.canteen-subscription-sweep-ms:60000}"
    )
    public void sweep() {

        expireSubscriptions();

        markCanteenNoShows();
    }

    private void expireSubscriptions() {

        List<UUID> subscriptionIds =
            jdbcTemplate.query(
                """
                SELECT subscription.id
                FROM subscriptions subscription
                JOIN subscription_plans plan
                  ON plan.id =
                     subscription.plan_id
                JOIN students student
                  ON student.id =
                     subscription.student_id
                JOIN campuses campus
                  ON campus.id =
                     student.campus_id
                 AND campus.organization_id =
                     plan.organization_id
                WHERE subscription.status = 'ACTIVE'
                  AND subscription.student_id IS NOT NULL
                  AND subscription.ends_at
                      < (
                            CURRENT_TIMESTAMP
                            AT TIME ZONE campus.timezone
                        )::date
                ORDER BY
                    subscription.ends_at ASC,
                    subscription.id ASC
                LIMIT ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                BATCH_SIZE
            );

        for (UUID subscriptionId : subscriptionIds) {

            try {

                subscriptionLifecycleService
                    .expireSystem(
                        subscriptionId
                    );
            }
            catch (RuntimeException exception) {

                LOGGER.warn(
                    "Automatic subscription expiration failed for subscription {}.",
                    subscriptionId,
                    exception
                );
            }
        }
    }

    private void markCanteenNoShows() {

        List<UUID> reservationIds =
            jdbcTemplate.query(
                """
                SELECT reservation.id
                FROM canteen_reservations reservation
                JOIN canteen_menus menu
                  ON menu.id =
                     reservation.menu_id
                JOIN locations location
                  ON location.id =
                     menu.location_id
                JOIN campuses campus
                  ON campus.id =
                     location.campus_id
                WHERE reservation.status = 'RESERVED'
                  AND reservation.student_id IS NOT NULL
                  AND menu.status IN (
                      'PUBLISHED',
                      'CLOSED'
                  )
                  AND menu.menu_date
                      < (
                            CURRENT_TIMESTAMP
                            AT TIME ZONE campus.timezone
                        )::date
                ORDER BY
                    menu.menu_date ASC,
                    reservation.id ASC
                LIMIT ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                BATCH_SIZE
            );

        for (UUID reservationId : reservationIds) {

            try {

                canteenLifecycleService
                    .markReservationNoShowSystem(
                        reservationId
                    );
            }
            catch (RuntimeException exception) {

                LOGGER.warn(
                    "Automatic canteen no-show transition failed for reservation {}.",
                    reservationId,
                    exception
                );
            }
        }
    }
}