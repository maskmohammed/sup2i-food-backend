package com.sup2i.food.subscription.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionLifecycleService {

    private final JdbcTemplate jdbcTemplate;

    public SubscriptionLifecycleService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public boolean expireSystem(
        UUID subscriptionId
    ) {

        if (subscriptionId == null) {
            throw new IllegalArgumentException(
                "subscriptionId is required."
            );
        }

        List<SubscriptionRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    subscription.id,
                    subscription.status,
                    subscription.ends_at,
                    plan.organization_id,
                    (
                        CURRENT_TIMESTAMP
                        AT TIME ZONE campus.timezone
                    )::date AS local_date
                FROM subscriptions subscription
                JOIN subscription_plans plan
                  ON plan.id = subscription.plan_id
                JOIN students student
                  ON student.id = subscription.student_id
                JOIN campuses campus
                  ON campus.id = student.campus_id
                 AND campus.organization_id =
                     plan.organization_id
                WHERE subscription.id = ?
                  AND subscription.student_id IS NOT NULL
                FOR UPDATE OF subscription
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new SubscriptionRow(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getObject(
                            "ends_at",
                            LocalDate.class
                        ),
                        resultSet.getObject(
                            "organization_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "local_date",
                            LocalDate.class
                        )
                    ),
                subscriptionId
            );

        if (rows.isEmpty()) {
            return false;
        }

        if (rows.size() != 1) {
            throw new IllegalStateException(
                "Subscription lookup returned multiple rows."
            );
        }

        SubscriptionRow subscription =
            rows.get(0);

        if (
            !"ACTIVE".equals(
                subscription.status()
            )
        ) {
            return false;
        }

        if (
            subscription.endsAt() == null
            || subscription.localDate() == null
        ) {
            throw new IllegalStateException(
                "Subscription validity date is unavailable."
            );
        }

        if (
            !subscription.endsAt()
                .isBefore(
                    subscription.localDate()
                )
        ) {
            return false;
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE subscriptions
                SET
                    status = 'EXPIRED',
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'ACTIVE'
                """,
                subscription.id()
            );

        if (updated != 1) {
            throw new IllegalStateException(
                "Subscription expiration update failed."
            );
        }

        int historyInserted =
            jdbcTemplate.update(
                """
                INSERT INTO subscription_status_history(
                    subscription_id,
                    from_status,
                    to_status,
                    changed_by,
                    reason
                )
                VALUES(
                    ?,
                    'ACTIVE',
                    'EXPIRED',
                    NULL,
                    'Subscription validity ended automatically.'
                )
                """,
                subscription.id()
            );

        if (historyInserted != 1) {
            throw new IllegalStateException(
                "Subscription expiration history insert failed."
            );
        }

        int auditInserted =
            jdbcTemplate.update(
                """
                INSERT INTO audit_logs(
                    organization_id,
                    user_id,
                    action,
                    resource_type,
                    resource_id,
                    before_data,
                    after_data,
                    reason,
                    source,
                    result
                )
                VALUES(
                    ?,
                    NULL,
                    'SUBSCRIPTION_EXPIRED',
                    'SUBSCRIPTION',
                    ?,
                    jsonb_build_object(
                        'status',
                        'ACTIVE'
                    ),
                    jsonb_build_object(
                        'status',
                        'EXPIRED'
                    ),
                    'Subscription validity ended automatically.',
                    'BACKEND',
                    'SUCCESS'
                )
                """,
                subscription.organizationId(),
                subscription.id()
            );

        if (auditInserted != 1) {
            throw new IllegalStateException(
                "Subscription expiration audit insert failed."
            );
        }

        return true;
    }

    private record SubscriptionRow(
        UUID id,
        String status,
        LocalDate endsAt,
        UUID organizationId,
        LocalDate localDate
    ) {
    }
}