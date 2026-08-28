package com.sup2i.food.dashboard.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbcTemplate;

    public DashboardRepository(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    public BigDecimal revenueBetween(
        UUID organizationId,
        OffsetDateTime from,
        OffsetDateTime to
    ) {

        BigDecimal result =
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(p.amount), 0)
                FROM payments p
                JOIN orders o
                    ON o.id = p.order_id
                WHERE o.organization_id = ?
                  AND p.status = 'COMPLETED'
                  AND p.paid_at >= ?
                  AND p.paid_at <= ?
                """,
                BigDecimal.class,
                organizationId,
                from,
                to
            );

        return result == null
            ? BigDecimal.ZERO
            : result;
    }

    public List<StatusCount>
        orderCountsByStatus(
            UUID organizationId
        ) {

        return jdbcTemplate.query(
            """
            SELECT status, COUNT(*) AS cnt
            FROM orders
            WHERE organization_id = ?
            GROUP BY status
            ORDER BY status
            """,
            (resultSet, rowNum) ->
                new StatusCount(
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getLong(
                        "cnt"
                    )
                ),
            organizationId
        );
    }

    public BigDecimal averageBasket(
        UUID organizationId
    ) {

        BigDecimal result =
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(AVG(total), 0)
                FROM orders
                WHERE organization_id = ?
                  AND payment_status = 'COMPLETED'
                """,
                BigDecimal.class,
                organizationId
            );

        return result == null
            ? BigDecimal.ZERO
            : result;
    }

    public List<ProductSales> topProducts(
        UUID organizationId,
        int limit
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                oi.product_id AS product_id,
                MAX(oi.product_name_snapshot) AS product_name,
                SUM(oi.quantity) AS qty
            FROM order_items oi
            JOIN orders o
                ON o.id = oi.order_id
            WHERE o.organization_id = ?
              AND o.payment_status = 'COMPLETED'
            GROUP BY oi.product_id
            ORDER BY SUM(oi.quantity) DESC
            LIMIT ?
            """,
            (resultSet, rowNum) ->
                new ProductSales(
                    UUID.fromString(
                        resultSet.getString(
                            "product_id"
                        )
                    ),
                    resultSet.getString(
                        "product_name"
                    ),
                    resultSet.getLong(
                        "qty"
                    )
                ),
            organizationId,
            limit
        );
    }

    public Double averagePreparationSeconds(
        UUID organizationId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT AVG(
                EXTRACT(
                    EPOCH FROM (
                        kt.ready_at - kt.started_at
                    )
                )
            )
            FROM kitchen_tickets kt
            JOIN orders o
                ON o.id = kt.order_id
            WHERE o.organization_id = ?
              AND kt.started_at IS NOT NULL
              AND kt.ready_at IS NOT NULL
            """,
            Double.class,
            organizationId
        );
    }

    public record StatusCount(
        String status,
        long count
    ) {
    }

    public record ProductSales(
        UUID productId,
        String productName,
        long quantitySold
    ) {
    }
}
