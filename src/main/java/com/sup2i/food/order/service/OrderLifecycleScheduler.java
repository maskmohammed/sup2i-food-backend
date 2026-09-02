package com.sup2i.food.order.service;

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
public class OrderLifecycleScheduler {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            OrderLifecycleScheduler.class
        );

    private static final int BATCH_SIZE =
        500;

    private final JdbcTemplate jdbcTemplate;

    private final OrderService orderService;

    public OrderLifecycleScheduler(
        JdbcTemplate jdbcTemplate,
        OrderService orderService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.orderService =
            orderService;
    }

    @Scheduled(
        fixedDelayString =
            "${sup2i.lifecycle.order-sweep-ms:60000}",
        initialDelayString =
            "${sup2i.lifecycle.order-sweep-ms:60000}"
    )
    public void sweep() {

        expireAwaitingPayment();

        markReadyNoShows();
        completeCollectedOrders();
    }

    private void expireAwaitingPayment() {

        List<Candidate> candidates =
            jdbcTemplate.query(
                """
                SELECT
                    organization_id,
                    id
                FROM orders
                WHERE status = 'AWAITING_PAYMENT'
                  AND payment_expires_at IS NOT NULL
                  AND payment_expires_at <= CURRENT_TIMESTAMP
                ORDER BY
                    payment_expires_at ASC,
                    id ASC
                LIMIT ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new Candidate(
                        resultSet.getObject(
                            "organization_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "id",
                            UUID.class
                        )
                    ),
                BATCH_SIZE
            );

        for (Candidate candidate : candidates) {

            try {

                orderService.expireSystem(
                    candidate.organizationId(),
                    candidate.orderId()
                );

            }
            catch (RuntimeException exception) {

                LOGGER.warn(
                    "Automatic order expiration failed for order {}.",
                    candidate.orderId(),
                    exception
                );
            }
        }
    }

    private void completeCollectedOrders() {

        java.util.List<java.util.Map<String, Object>> rows =
            jdbcTemplate.queryForList(
                """
                SELECT
                    organization_id,
                    id
                FROM orders
                WHERE status = 'COLLECTED'
                ORDER BY
                    collected_at ASC NULLS LAST,
                    id ASC
                LIMIT ?
                """,
                BATCH_SIZE
            );

        for (java.util.Map<String, Object> row : rows) {

            UUID organizationId =
                (UUID) row.get(
                    "organization_id"
                );

            UUID orderId =
                (UUID) row.get(
                    "id"
                );

            try {

                orderService.completeSystem(
                    organizationId,
                    orderId
                );
            }
            catch (RuntimeException exception) {

                org.slf4j.LoggerFactory
                    .getLogger(
                        OrderLifecycleScheduler.class
                    )
                    .warn(
                        "Order SYSTEM completion failed for order {}.",
                        orderId,
                        exception
                    );
            }
        }
    }
    private void markReadyNoShows() {

        List<Candidate> candidates =
            jdbcTemplate.query(
                """
                SELECT
                    organization_id,
                    id
                FROM orders
                WHERE status = 'READY'
                  AND ready_at IS NOT NULL
                ORDER BY
                    ready_at ASC,
                    id ASC
                LIMIT ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new Candidate(
                        resultSet.getObject(
                            "organization_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "id",
                            UUID.class
                        )
                    ),
                BATCH_SIZE
            );

        for (Candidate candidate : candidates) {

            try {

                orderService.markNoShowSystem(
                    candidate.organizationId(),
                    candidate.orderId()
                );

            }
            catch (RuntimeException exception) {

                LOGGER.warn(
                    "Automatic order no-show transition failed for order {}.",
                    candidate.orderId(),
                    exception
                );
            }
        }
    }

    private record Candidate(
        UUID organizationId,
        UUID orderId
    ) {
    }
}