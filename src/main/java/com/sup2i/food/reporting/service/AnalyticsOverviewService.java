package com.sup2i.food.reporting.service;

import com.sup2i.food.reporting.api.dto.AnalyticsOverviewResponse;
import com.sup2i.food.reporting.api.dto.AnalyticsTopProductResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AnalyticsOverviewService {

    private static final MathContext
        CALCULATION_CONTEXT =
            MathContext.DECIMAL128;

    private static final int
        TOP_PRODUCT_LIMIT =
            10;

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsOverviewService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse overview(
        UUID actorId,
        UUID campusId,
        OffsetDateTime from,
        OffsetDateTime to
    ) {

        validateRange(
            from,
            to
        );

        UUID organizationId =
            organizationId(
                actorId
            );

        validateCampus(
            organizationId,
            campusId
        );

        SalesMetrics sales =
            salesMetrics(
                organizationId,
                campusId,
                from,
                to
            );

        long activeSubscriptions =
            activeCanteenSubscriptions(
                organizationId,
                campusId,
                from,
                to
            );

        long mealsDistributed =
            mealsDistributed(
                organizationId,
                campusId,
                from,
                to
            );

        BigDecimal wasteCost =
            wasteCost(
                organizationId,
                campusId,
                from,
                to
            );

        List<AnalyticsTopProductResponse>
            topProducts =
                topProducts(
                    organizationId,
                    campusId,
                    from,
                    to
                );

        BigDecimal averageBasket =
            averageBasket(
                sales.revenue(),
                sales.orders()
            );

        return new AnalyticsOverviewResponse(
            sales.revenue(),
            sales.orders(),
            averageBasket,
            sales.mobileSales(),
            sales.posSales(),
            activeSubscriptions,
            mealsDistributed,
            wasteCost,
            List.copyOf(
                topProducts
            )
        );
    }

    private SalesMetrics salesMetrics(
        UUID organizationId,
        UUID campusId,
        OffsetDateTime from,
        OffsetDateTime to
    ) {

        List<SalesMetrics> rows =
            jdbcTemplate.query(
                """
                WITH eligible_payments AS (
                    SELECT
                        payment.order_id,
                        food_order.source,
                        GREATEST(
                            payment.amount
                                - COALESCE(
                                    (
                                        SELECT
                                            SUM(refund.amount)
                                        FROM refunds refund
                                        WHERE refund.payment_id =
                                              payment.id
                                          AND refund.status =
                                              'COMPLETED'
                                    ),
                                    0
                                ),
                            0
                        ) AS net_amount
                    FROM payments payment
                    JOIN orders food_order
                      ON food_order.id =
                         payment.order_id
                    WHERE food_order.organization_id = ?
                      AND food_order.source
                          IN (
                              'MOBILE',
                              'POS'
                          )
                      AND payment.status
                          IN (
                              'COMPLETED',
                              'PARTIALLY_REFUNDED',
                              'REFUNDED'
                          )
                      AND payment.paid_at >= ?
                      AND payment.paid_at < ?
                      AND (
                            CAST(? AS UUID) IS NULL
                            OR food_order.campus_id = ?
                          )
                ),
                paid_orders AS (
                    SELECT
                        order_id,
                        source,
                        SUM(net_amount) AS net_amount
                    FROM eligible_payments
                    GROUP BY
                        order_id,
                        source
                    HAVING
                        SUM(net_amount) > 0
                )
                SELECT
                    COALESCE(
                        SUM(net_amount),
                        0
                    ) AS revenue,
                    COUNT(*) AS orders,
                    COALESCE(
                        SUM(net_amount)
                            FILTER (
                                WHERE source = 'MOBILE'
                            ),
                        0
                    ) AS mobile_sales,
                    COALESCE(
                        SUM(net_amount)
                            FILTER (
                                WHERE source = 'POS'
                            ),
                        0
                    ) AS pos_sales
                FROM paid_orders
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new SalesMetrics(
                        resultSet.getBigDecimal(
                            "revenue"
                        ),
                        resultSet.getLong(
                            "orders"
                        ),
                        resultSet.getBigDecimal(
                            "mobile_sales"
                        ),
                        resultSet.getBigDecimal(
                            "pos_sales"
                        )
                    ),
                organizationId,
                from,
                to,
                campusId,
                campusId
            );

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Analytics sales aggregation returned an invalid result."
            );
        }

        return rows.get(0);
    }

    private long activeCanteenSubscriptions(
        UUID organizationId,
        UUID campusId,
        OffsetDateTime from,
        OffsetDateTime to
    ) {

        Long value =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
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
                WHERE plan.organization_id = ?
                  AND subscription.student_id
                      IS NOT NULL
                  AND subscription.meal_beneficiary_id
                      IS NULL
                  AND subscription.status =
                      'ACTIVE'
                  AND subscription.starts_at
                      <= (
                            (
                                CAST(? AS TIMESTAMPTZ)
                                - INTERVAL '1 microsecond'
                            )
                            AT TIME ZONE campus.timezone
                         )::date
                  AND subscription.ends_at
                      >= (
                            CAST(? AS TIMESTAMPTZ)
                            AT TIME ZONE campus.timezone
                         )::date
                  AND (
                        CAST(? AS UUID) IS NULL
                        OR campus.id = ?
                      )
                """,
                Long.class,
                organizationId,
                to,
                from,
                campusId,
                campusId
            );

        return value == null
            ? 0L
            : value;
    }

    private long mealsDistributed(
        UUID organizationId,
        UUID campusId,
        OffsetDateTime from,
        OffsetDateTime to
    ) {

        Long value =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM meal_usages usage
                JOIN meal_entitlements entitlement
                  ON entitlement.id =
                     usage.entitlement_id
                JOIN subscriptions subscription
                  ON subscription.id =
                     entitlement.subscription_id
                JOIN subscription_plans plan
                  ON plan.id =
                     subscription.plan_id
                JOIN students student
                  ON student.id =
                     usage.student_id
                JOIN campuses campus
                  ON campus.id =
                     student.campus_id
                WHERE plan.organization_id = ?
                  AND campus.organization_id = ?
                  AND subscription.student_id =
                      usage.student_id
                  AND subscription.meal_beneficiary_id
                      IS NULL
                  AND usage.meal_beneficiary_id
                      IS NULL
                  AND usage.status =
                      'VALID'
                  AND usage.consumed_at >= ?
                  AND usage.consumed_at < ?
                  AND (
                        CAST(? AS UUID) IS NULL
                        OR campus.id = ?
                      )
                """,
                Long.class,
                organizationId,
                organizationId,
                from,
                to,
                campusId,
                campusId
            );

        return value == null
            ? 0L
            : value;
    }

    private BigDecimal wasteCost(
        UUID organizationId,
        UUID campusId,
        OffsetDateTime from,
        OffsetDateTime to
    ) {

        BigDecimal value =
            jdbcTemplate.queryForObject(
                """
                SELECT
                    COALESCE(
                        SUM(
                            COALESCE(
                                waste.estimated_cost,
                                0
                            )
                        ),
                        0
                    )
                FROM waste_records waste
                JOIN stock_items stock_item
                  ON stock_item.id =
                     waste.stock_item_id
                JOIN stock_locations stock_location
                  ON stock_location.id =
                     waste.stock_location_id
                JOIN locations location
                  ON location.id =
                     stock_location.location_id
                JOIN campuses campus
                  ON campus.id =
                     location.campus_id
                WHERE stock_item.organization_id = ?
                  AND campus.organization_id = ?
                  AND waste.recorded_at >= ?
                  AND waste.recorded_at < ?
                  AND (
                        CAST(? AS UUID) IS NULL
                        OR campus.id = ?
                      )
                """,
                BigDecimal.class,
                organizationId,
                organizationId,
                from,
                to,
                campusId,
                campusId
            );

        return value == null
            ? BigDecimal.ZERO
            : value;
    }

    private List<AnalyticsTopProductResponse>
        topProducts(
            UUID organizationId,
            UUID campusId,
            OffsetDateTime from,
            OffsetDateTime to
        ) {

        /*
         * Refunds are payment-level in the frozen schema.
         * There is no normative line-level refund allocation.
         *
         * Fully refunded net-zero orders are excluded.
         * Quantity and revenue therefore use sold line values
         * for orders retaining a positive net paid value.
         */
        return jdbcTemplate.query(
            """
            WITH eligible_payments AS (
                SELECT
                    payment.order_id,
                    GREATEST(
                        payment.amount
                            - COALESCE(
                                (
                                    SELECT
                                        SUM(refund.amount)
                                    FROM refunds refund
                                    WHERE refund.payment_id =
                                          payment.id
                                      AND refund.status =
                                          'COMPLETED'
                                ),
                                0
                            ),
                        0
                    ) AS net_amount
                FROM payments payment
                JOIN orders food_order
                  ON food_order.id =
                     payment.order_id
                WHERE food_order.organization_id = ?
                  AND food_order.source
                      IN (
                          'MOBILE',
                          'POS'
                      )
                  AND payment.status
                      IN (
                          'COMPLETED',
                          'PARTIALLY_REFUNDED',
                          'REFUNDED'
                      )
                  AND payment.paid_at >= ?
                  AND payment.paid_at < ?
                  AND (
                        CAST(? AS UUID) IS NULL
                        OR food_order.campus_id = ?
                      )
            ),
            positive_orders AS (
                SELECT
                    order_id
                FROM eligible_payments
                GROUP BY order_id
                HAVING SUM(net_amount) > 0
            )
            SELECT
                item.product_id,
                MAX(
                    item.product_name_snapshot
                ) AS product_name,
                SUM(
                    item.quantity
                )::BIGINT AS quantity,
                COALESCE(
                    SUM(
                        item.line_total
                    ),
                    0
                ) AS product_revenue
            FROM positive_orders paid_order
            JOIN order_items item
              ON item.order_id =
                 paid_order.order_id
            GROUP BY
                item.product_id
            ORDER BY
                quantity DESC,
                product_revenue DESC,
                item.product_id ASC
            LIMIT ?
            """,
            (
                resultSet,
                rowNumber
            ) ->
                new AnalyticsTopProductResponse(
                    resultSet.getObject(
                        "product_id",
                        UUID.class
                    ),
                    resultSet.getString(
                        "product_name"
                    ),
                    resultSet.getLong(
                        "quantity"
                    ),
                    resultSet.getBigDecimal(
                        "product_revenue"
                    )
                ),
            organizationId,
            from,
            to,
            campusId,
            campusId,
            TOP_PRODUCT_LIMIT
        );
    }

    private UUID organizationId(
        UUID actorId
    ) {

        if (actorId == null) {

            throw new BadCredentialsException(
                "Authenticated user identifier is missing."
            );
        }

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT user_account.organization_id
                FROM users user_account
                JOIN organizations organization
                  ON organization.id =
                     user_account.organization_id
                WHERE user_account.id = ?
                  AND user_account.status =
                      'ACTIVE'
                  AND organization.is_active =
                      TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "organization_id",
                        UUID.class
                    ),
                actorId
            );

        if (rows.isEmpty()) {

            throw new BadCredentialsException(
                "Authenticated user is inactive or does not exist."
            );
        }

        if (rows.size() != 1) {

            throw new IllegalStateException(
                "Analytics actor lookup returned multiple rows."
            );
        }

        return rows.get(0);
    }

    private void validateCampus(
        UUID organizationId,
        UUID campusId
    ) {

        if (campusId == null) {
            return;
        }

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM campuses campus
                WHERE campus.id = ?
                  AND campus.organization_id = ?
                  AND campus.is_active = TRUE
                """,
                Integer.class,
                campusId,
                organizationId
            );

        if (
            count == null
            || count != 1
        ) {

            throw new IllegalArgumentException(
                "campusId does not identify an active campus in the current organization."
            );
        }
    }

    private void validateRange(
        OffsetDateTime from,
        OffsetDateTime to
    ) {

        if (
            from == null
            || to == null
        ) {

            throw new IllegalArgumentException(
                "from and to are required."
            );
        }

        if (
            !to.isAfter(
                from
            )
        ) {

            throw new IllegalArgumentException(
                "to must be strictly after from."
            );
        }
    }

    private BigDecimal averageBasket(
        BigDecimal revenue,
        long orders
    ) {

        if (orders == 0L) {
            return null;
        }

        return revenue.divide(
            BigDecimal.valueOf(
                orders
            ),
            CALCULATION_CONTEXT
        );
    }

    private record SalesMetrics(
        BigDecimal revenue,
        long orders,
        BigDecimal mobileSales,
        BigDecimal posSales
    ) {
    }
}