package com.sup2i.food.slot.service;

import com.sup2i.food.order.api.dto.QueueEstimateResponse;
import com.sup2i.food.order.domain.OrderStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class VirtualQueueService {

    private final JdbcTemplate jdbcTemplate;

    public VirtualQueueService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    /*
     * The virtual queue is a projection of the real KDS
     * workload. It does not persist a duplicate queue.
     *
     * Current frozen KDS order:
     *   priority DESC,
     *   queued_at ASC,
     *   ticket id ASC.
     *
     * A multi-kitchen order waits for all of its kitchens:
     * estimatedMinutes is therefore the maximum remaining
     * lane workload among the order's active kitchen tickets.
     *
     * Preparation time is data-driven:
     * product_location_settings.preparation_minutes overrides
     * products.preparation_minutes.
     */
    @Transactional(readOnly = true)
    public QueueEstimateResponse estimate(
        UUID organizationId,
        UUID orderId,
        OrderStatus orderStatus
    ) {

        if (
            orderStatus != OrderStatus.QUEUED
            && orderStatus != OrderStatus.PREPARING
        ) {
            return null;
        }

        if (
            organizationId == null
            || orderId == null
        ) {

            throw new IllegalArgumentException(
                "Queue estimate requires organization and order identifiers."
            );
        }

        List<QueueProjection> projections =
            jdbcTemplate.query(
                """
                WITH target AS (
                    SELECT
                        kt.id,
                        kt.order_id,
                        kt.kitchen_location_id,
                        kt.priority,
                        kt.queued_at
                    FROM kitchen_tickets kt
                    JOIN orders o
                      ON o.id = kt.order_id
                    WHERE kt.order_id = ?
                      AND o.organization_id = ?
                      AND kt.status IN (
                          'QUEUED',
                          'ACCEPTED',
                          'PREPARING'
                      )
                ),
                active AS (
                    SELECT
                        kt.id,
                        kt.order_id,
                        kt.kitchen_location_id,
                        kt.priority,
                        kt.queued_at,
                        kt.started_at,
                        kt.status
                    FROM kitchen_tickets kt
                    JOIN orders o
                      ON o.id = kt.order_id
                    WHERE o.organization_id = ?
                      AND kt.status IN (
                          'QUEUED',
                          'ACCEPTED',
                          'PREPARING'
                      )
                      AND EXISTS (
                          SELECT 1
                          FROM target t
                          WHERE t.kitchen_location_id =
                                kt.kitchen_location_id
                      )
                ),
                ticket_prep AS (
                    SELECT
                        a.id AS ticket_id,
                        COALESCE(
                            MAX(
                                COALESCE(
                                    pls.preparation_minutes,
                                    p.preparation_minutes,
                                    0
                                )
                            ),
                            0
                        )::INTEGER AS prep_minutes
                    FROM active a
                    LEFT JOIN orders o
                      ON o.id = a.order_id
                    LEFT JOIN kitchen_ticket_items kti
                      ON kti.kitchen_ticket_id = a.id
                    LEFT JOIN order_items oi
                      ON oi.id = kti.order_item_id
                    LEFT JOIN products p
                      ON p.id = oi.product_id
                    LEFT JOIN product_location_settings pls
                      ON pls.product_id = p.id
                     AND pls.location_id = o.location_id
                    GROUP BY
                        a.id
                ),
                ahead AS (
                    SELECT DISTINCT
                        a.id AS ticket_id,
                        a.order_id,
                        a.kitchen_location_id
                    FROM target t
                    JOIN active a
                      ON a.kitchen_location_id =
                         t.kitchen_location_id
                    WHERE a.id <> t.id
                      AND (
                          a.priority > t.priority
                          OR (
                              a.priority = t.priority
                              AND a.queued_at < t.queued_at
                          )
                          OR (
                              a.priority = t.priority
                              AND a.queued_at = t.queued_at
                              AND a.id < t.id
                          )
                      )
                ),
                lane_work AS (
                    SELECT
                        t.kitchen_location_id,
                        COALESCE(
                            SUM(
                                CASE
                                    WHEN a.id = t.id
                                      OR EXISTS (
                                          SELECT 1
                                          FROM ahead h
                                          WHERE h.ticket_id = a.id
                                            AND h.kitchen_location_id =
                                                t.kitchen_location_id
                                      )
                                    THEN GREATEST(
                                        0,
                                        tp.prep_minutes
                                        -
                                        CASE
                                            WHEN a.status = 'PREPARING'
                                             AND a.started_at IS NOT NULL
                                            THEN FLOOR(
                                                EXTRACT(
                                                    EPOCH FROM (
                                                        CURRENT_TIMESTAMP
                                                        - a.started_at
                                                    )
                                                ) / 60
                                            )::INTEGER
                                            ELSE 0
                                        END
                                    )
                                    ELSE 0
                                END
                            ),
                            0
                        )::INTEGER AS estimated_minutes
                    FROM target t
                    JOIN active a
                      ON a.kitchen_location_id =
                         t.kitchen_location_id
                    JOIN ticket_prep tp
                      ON tp.ticket_id = a.id
                    GROUP BY
                        t.kitchen_location_id
                )
                SELECT
                    (
                        SELECT COUNT(*)
                        FROM target
                    )::INTEGER AS target_tickets,
                    (
                        SELECT COUNT(
                            DISTINCT order_id
                        )
                        FROM ahead
                    )::INTEGER AS orders_ahead,
                    COALESCE(
                        (
                            SELECT MAX(
                                estimated_minutes
                            )
                            FROM lane_work
                        ),
                        0
                    )::INTEGER AS estimated_minutes
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new QueueProjection(
                        resultSet.getInt(
                            "target_tickets"
                        ),
                        resultSet.getInt(
                            "orders_ahead"
                        ),
                        resultSet.getInt(
                            "estimated_minutes"
                        )
                    ),
                orderId,
                organizationId,
                organizationId
            );

        if (projections.size() != 1) {

            throw new IllegalStateException(
                "Queue estimate query did not return exactly one row."
            );
        }

        QueueProjection projection =
            projections.get(0);

        if (
            projection.targetTickets()
                <= 0
        ) {

            throw new IllegalStateException(
                "Active order has no active kitchen ticket."
            );
        }

        return new QueueEstimateResponse(
            projection.ordersAhead(),
            projection.estimatedMinutes()
        );
    }

    private record QueueProjection(
        int targetTickets,
        int ordersAhead,
        int estimatedMinutes
    ) {
    }
}