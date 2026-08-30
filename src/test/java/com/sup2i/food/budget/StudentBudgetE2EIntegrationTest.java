package com.sup2i.food.budget;

import com.sup2i.food.budget.model.StudentBudgetSnapshot;
import com.sup2i.food.budget.model.StudentBudgetSnapshot.SpendByCurrency;
import com.sup2i.food.budget.model.StudentBudgetSnapshot.SubscriptionComparison;
import com.sup2i.food.budget.service.StudentBudgetService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@ActiveProfiles("test")
@Testcontainers
class StudentBudgetE2EIntegrationTest {

    private static final ZoneId CAMPUS_ZONE =
        ZoneId.of(
            "Africa/Casablanca"
        );

    private static final YearMonth AUGUST_2026 =
        YearMonth.of(
            2026,
            8
        );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName(
                "sup2i_food_test"
            );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudentBudgetService budgetService;

    private UUID organizationId;
    private UUID campusId;
    private UUID locationId;
    private StudentFixture student;

    @BeforeEach
    void seedStudent() {

        organizationId =
            insertOrganization(
                "B9"
            );

        campusId =
            insertCampus(
                organizationId,
                "Africa/Casablanca"
            );

        locationId =
            insertLocation(
                campusId
            );

        student =
            insertStudent(
                organizationId,
                campusId
            );
    }

    @Test
    void readIsTenantSafeAndUnknownStudentReturnsEmpty() {

        Optional<StudentBudgetSnapshot> own =
            budgetService.read(
                organizationId,
                student.studentId(),
                AUGUST_2026
            );

        assertThat(own)
            .isPresent();

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN"
            );

        Optional<StudentBudgetSnapshot> foreign =
            budgetService.read(
                foreignOrganization,
                student.studentId(),
                AUGUST_2026
            );

        assertThat(foreign)
            .isEmpty();

        Optional<StudentBudgetSnapshot> unknown =
            budgetService.read(
                organizationId,
                UUID.randomUUID(),
                AUGUST_2026
            );

        assertThat(unknown)
            .isEmpty();
    }

    @Test
    void monthlySpendUsesPaidPaymentsAndCompletedRefundsOnly() {

        UUID completed =
            insertPayment(
                student,
                new BigDecimal("40.00"),
                "MAD",
                "COMPLETED",
                OffsetDateTime.parse(
                    "2026-08-10T12:00:00Z"
                )
            );

        UUID partial =
            insertPayment(
                student,
                new BigDecimal("30.00"),
                "MAD",
                "PARTIALLY_REFUNDED",
                OffsetDateTime.parse(
                    "2026-08-12T12:00:00Z"
                )
            );

        insertRefund(
            partial,
            new BigDecimal("10.00"),
            "COMPLETED",
            OffsetDateTime.parse(
                "2026-09-05T10:00:00Z"
            )
        );

        insertRefund(
            completed,
            new BigDecimal("5.00"),
            "REQUESTED",
            null
        );

        insertPayment(
            student,
            new BigDecimal("100.00"),
            "MAD",
            "FAILED",
            OffsetDateTime.parse(
                "2026-08-15T12:00:00Z"
            )
        );

        insertPayment(
            student,
            new BigDecimal("50.00"),
            "MAD",
            "COMPLETED",
            OffsetDateTime.parse(
                "2026-09-03T12:00:00Z"
            )
        );

        StudentBudgetSnapshot snapshot =
            readAugust();

        SpendByCurrency mad =
            spend(
                snapshot,
                "MAD"
            );

        assertThat(mad.paymentCount())
            .isEqualTo(2);

        assertThat(mad.grossPaid())
            .isEqualByComparingTo(
                "70.00"
            );

        assertThat(mad.completedRefunds())
            .isEqualByComparingTo(
                "10.00"
            );

        assertThat(mad.netSpend())
            .isEqualByComparingTo(
                "60.00"
            );
    }

    @Test
    void monthlyBoundaryUsesStudentCampusTimezone() {

        insertPayment(
            student,
            new BigDecimal("12.00"),
            "MAD",
            "COMPLETED",
            OffsetDateTime.parse(
                "2026-07-31T23:30:00Z"
            )
        );

        insertPayment(
            student,
            new BigDecimal("99.00"),
            "MAD",
            "COMPLETED",
            OffsetDateTime.parse(
                "2026-08-31T23:30:00Z"
            )
        );

        StudentBudgetSnapshot snapshot =
            readAugust();

        assertThat(snapshot.periodStart())
            .isEqualTo(
                LocalDate.of(
                    2026,
                    8,
                    1
                )
            );

        assertThat(snapshot.periodEnd())
            .isEqualTo(
                LocalDate.of(
                    2026,
                    8,
                    31
                )
            );

        assertThat(snapshot.campusTimezone())
            .isEqualTo(
                "Africa/Casablanca"
            );

        SpendByCurrency mad =
            spend(
                snapshot,
                "MAD"
            );

        assertThat(mad.paymentCount())
            .isEqualTo(1);

        assertThat(mad.netSpend())
            .isEqualByComparingTo(
                "12.00"
            );

        LocalDate includedLocalDate =
            OffsetDateTime
                .parse(
                    "2026-07-31T23:30:00Z"
                )
                .atZoneSameInstant(
                    CAMPUS_ZONE
                )
                .toLocalDate();

        LocalDate excludedLocalDate =
            OffsetDateTime
                .parse(
                    "2026-08-31T23:30:00Z"
                )
                .atZoneSameInstant(
                    CAMPUS_ZONE
                )
                .toLocalDate();

        assertThat(includedLocalDate)
            .isEqualTo(
                LocalDate.of(
                    2026,
                    8,
                    1
                )
            );

        assertThat(excludedLocalDate)
            .isEqualTo(
                LocalDate.of(
                    2026,
                    9,
                    1
                )
            );
    }

    @Test
    void budgetAssessmentUsesBudgetCurrencyWithoutFxMerge() {

        insertBudgetSettings(
            student.studentId(),
            new BigDecimal("100.00"),
            "MAD",
            new BigDecimal("80.00"),
            true
        );

        insertPayment(
            student,
            new BigDecimal("85.00"),
            "MAD",
            "COMPLETED",
            OffsetDateTime.parse(
                "2026-08-11T12:00:00Z"
            )
        );

        insertPayment(
            student,
            new BigDecimal("20.00"),
            "EUR",
            "COMPLETED",
            OffsetDateTime.parse(
                "2026-08-12T12:00:00Z"
            )
        );

        StudentBudgetSnapshot snapshot =
            readAugust();

        assertThat(snapshot.spendByCurrency())
            .hasSize(2);

        assertThat(
            spend(
                snapshot,
                "MAD"
            ).netSpend()
        )
            .isEqualByComparingTo(
                "85.00"
            );

        assertThat(
            spend(
                snapshot,
                "EUR"
            ).netSpend()
        )
            .isEqualByComparingTo(
                "20.00"
            );

        assertThat(snapshot.budget())
            .isNotNull();

        assertThat(
            snapshot
                .budget()
                .monthlyBudget()
        )
            .isEqualByComparingTo(
                "100.00"
            );

        assertThat(
            snapshot
                .budget()
                .netSpendInBudgetCurrency()
        )
            .isEqualByComparingTo(
                "85.00"
            );

        assertThat(
            snapshot
                .budget()
                .usagePct()
        )
            .isEqualByComparingTo(
                "85.00"
            );

        assertThat(
            snapshot
                .budget()
                .alertThresholdAmount()
        )
            .isEqualByComparingTo(
                "80.00"
            );

        assertThat(
            snapshot
                .budget()
                .alertThresholdReached()
        )
            .isTrue();

        assertThat(
            snapshot
                .budget()
                .otherCurrencySpendPresent()
        )
            .isTrue();
    }

    @Test
    void missingBudgetSettingsDoesNotHideActualSpend() {

        insertPayment(
            student,
            new BigDecimal("27.50"),
            "MAD",
            "COMPLETED",
            OffsetDateTime.parse(
                "2026-08-20T12:00:00Z"
            )
        );

        StudentBudgetSnapshot snapshot =
            readAugust();

        assertThat(snapshot.budget())
            .isNull();

        assertThat(snapshot.spendByCurrency())
            .hasSize(1);

        assertThat(
            spend(
                snapshot,
                "MAD"
            ).netSpend()
        )
            .isEqualByComparingTo(
                "27.50"
            );
    }

    @Test
    void subscriptionComparisonUsesSubscribedVersionSnapshot() {

        SubscriptionFixture subscription =
            insertSubscription(
                student.studentId(),
                LocalDate.of(
                    2026,
                    7,
                    15
                ),
                LocalDate.of(
                    2026,
                    9,
                    15
                ),
                new BigDecimal("999.00"),
                new BigDecimal("125.00"),
                new BigDecimal("100.00"),
                "ADMIN-B9-PAID"
            );

        insertSubscription(
            student.studentId(),
            LocalDate.of(
                2026,
                10,
                1
            ),
            LocalDate.of(
                2026,
                10,
                31
            ),
            new BigDecimal("500.00"),
            new BigDecimal("400.00"),
            null,
            null
        );

        StudentBudgetSnapshot snapshot =
            readAugust();

        assertThat(snapshot.subscriptions())
            .hasSize(1);

        SubscriptionComparison comparison =
            snapshot
                .subscriptions()
                .get(0);

        assertThat(comparison.subscriptionId())
            .isEqualTo(
                subscription.subscriptionId()
            );

        assertThat(comparison.planId())
            .isEqualTo(
                subscription.planId()
            );

        assertThat(comparison.planVersionId())
            .isEqualTo(
                subscription.planVersionId()
            );

        assertThat(comparison.status())
            .isEqualTo(
                "ACTIVE"
            );

        assertThat(comparison.billingPeriod())
            .isEqualTo(
                "MONTH"
            );

        assertThat(comparison.subscribedSnapshotPrice())
            .isEqualByComparingTo(
                "125.00"
            );

        assertThat(comparison.administrativePaymentAmount())
            .isEqualByComparingTo(
                "100.00"
            );

        assertThat(comparison.paymentReference())
            .isEqualTo(
                "ADMIN-B9-PAID"
            );

        BigDecimal currentPlanPrice =
            jdbcTemplate.queryForObject(
                """
                SELECT price
                FROM subscription_plans
                WHERE id = ?
                """,
                BigDecimal.class,
                subscription.planId()
            );

        assertThat(currentPlanPrice)
            .isEqualByComparingTo(
                "999.00"
            );

        assertThat(
            comparison
                .subscribedSnapshotPrice()
        )
            .isNotEqualByComparingTo(
                currentPlanPrice
            );
    }

    private StudentBudgetSnapshot readAugust() {

        return budgetService
            .read(
                organizationId,
                student.studentId(),
                AUGUST_2026
            )
            .orElseThrow();
    }

    private SpendByCurrency spend(
        StudentBudgetSnapshot snapshot,
        String currency
    ) {

        return snapshot
            .spendByCurrency()
            .stream()
            .filter(
                current ->
                    currency.equals(
                        current.currency()
                    )
            )
            .findFirst()
            .orElseThrow();
    }

    private UUID insertOrganization(
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO organizations (
                id,
                name,
                code,
                is_active
            )
            VALUES (
                ?, ?, ?, TRUE
            )
            """,
            id,
            prefix
                + " Organization",
            prefix
                + "-"
                + suffix()
        );

        return id;
    }

    private UUID insertCampus(
        UUID selectedOrganizationId,
        String timezone
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO campuses (
                id,
                organization_id,
                name,
                code,
                timezone,
                is_active
            )
            VALUES (
                ?, ?, ?, ?, ?, TRUE
            )
            """,
            id,
            selectedOrganizationId,
            "B9 Campus",
            "B9-C-"
                + suffix(),
            timezone
        );

        return id;
    }

    private UUID insertLocation(
        UUID selectedCampusId
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO locations (
                id,
                campus_id,
                name,
                code,
                type,
                is_active
            )
            VALUES (
                ?, ?, ?, ?,
                'SNACK',
                TRUE
            )
            """,
            id,
            selectedCampusId,
            "B9 Location",
            "B9-L-"
                + suffix()
        );

        return id;
    }

    private StudentFixture insertStudent(
        UUID selectedOrganizationId,
        UUID selectedCampusId
    ) {

        UUID userId =
            UUID.randomUUID();

        UUID studentId =
            UUID.randomUUID();

        String random =
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO users (
                id,
                organization_id,
                email,
                first_name,
                last_name,
                status
            )
            VALUES (
                ?, ?, ?,
                'Budget',
                'Student',
                'ACTIVE'
            )
            """,
            userId,
            selectedOrganizationId,
            "b9-"
                + random
                + "@sup2i.test"
        );

        jdbcTemplate.update(
            """
            INSERT INTO students (
                id,
                user_id,
                campus_id,
                student_number,
                enrollment_status
            )
            VALUES (
                ?, ?, ?, ?,
                'ACTIVE'
            )
            """,
            studentId,
            userId,
            selectedCampusId,
            "B9-"
                + random
        );

        return new StudentFixture(
            userId,
            studentId
        );
    }

    private void insertBudgetSettings(
        UUID selectedStudentId,
        BigDecimal monthlyBudget,
        String currency,
        BigDecimal threshold,
        boolean enabled
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO student_budget_settings (
                student_id,
                monthly_budget,
                currency,
                alert_threshold_pct,
                is_enabled
            )
            VALUES (
                ?, ?, ?, ?, ?
            )
            """,
            selectedStudentId,
            monthlyBudget,
            currency,
            threshold,
            enabled
        );
    }

    private UUID insertPayment(
        StudentFixture selectedStudent,
        BigDecimal amount,
        String currency,
        String paymentStatus,
        OffsetDateTime paidAt
    ) {

        UUID orderId =
            UUID.randomUUID();

        LocalDate businessDate =
            paidAt
                .atZoneSameInstant(
                    CAMPUS_ZONE
                )
                .toLocalDate();

        String orderPaymentStatus =
            switch (paymentStatus) {
                case "COMPLETED" ->
                    "COMPLETED";

                case "PARTIALLY_REFUNDED" ->
                    "PARTIALLY_REFUNDED";

                case "REFUNDED" ->
                    "REFUNDED";

                default ->
                    "FAILED";
            };

        jdbcTemplate.update(
            """
            INSERT INTO orders (
                id,
                organization_id,
                campus_id,
                location_id,
                student_id,
                order_number,
                business_date,
                source,
                status,
                subtotal,
                discount_total,
                total,
                currency,
                paid_at,
                payment_status
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?,
                'MOBILE',
                'COMPLETED',
                ?, 0, ?, ?, ?, ?
            )
            """,
            orderId,
            organizationId,
            campusId,
            locationId,
            selectedStudent.studentId(),
            "B9-ORDER-"
                + suffix(),
            businessDate,
            amount,
            amount,
            currency,
            paidAt,
            orderPaymentStatus
        );

        UUID paymentId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO payments (
                id,
                order_id,
                method,
                status,
                amount,
                currency,
                received_by,
                paid_at
            )
            VALUES (
                ?, ?,
                'CASH',
                ?, ?, ?, ?, ?
            )
            """,
            paymentId,
            orderId,
            paymentStatus,
            amount,
            currency,
            selectedStudent.userId(),
            paidAt
        );

        return paymentId;
    }

    private void insertRefund(
        UUID paymentId,
        BigDecimal amount,
        String status,
        OffsetDateTime completedAt
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO refunds (
                id,
                payment_id,
                amount,
                reason,
                status,
                requested_by,
                approved_by,
                completed_at
            )
            VALUES (
                ?, ?, ?,
                'B9 budget test',
                ?, ?, ?, ?
            )
            """,
            UUID.randomUUID(),
            paymentId,
            amount,
            status,
            student.userId(),
            student.userId(),
            completedAt
        );
    }

    private SubscriptionFixture insertSubscription(
        UUID selectedStudentId,
        LocalDate startsAt,
        LocalDate endsAt,
        BigDecimal mutablePlanPrice,
        BigDecimal snapshotPrice,
        BigDecimal administrativeAmount,
        String paymentReference
    ) {

        UUID planId =
            UUID.randomUUID();

        UUID planVersionId =
            UUID.randomUUID();

        UUID subscriptionId =
            UUID.randomUUID();

        String random =
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO subscription_plans (
                id,
                organization_id,
                name,
                code,
                billing_period,
                price,
                reservation_required,
                is_active,
                audience_type
            )
            VALUES (
                ?, ?, ?, ?,
                'MONTH',
                ?,
                FALSE,
                TRUE,
                'STUDENT'
            )
            """,
            planId,
            organizationId,
            "B9 Plan "
                + random,
            "B9-PLAN-"
                + random,
            mutablePlanPrice
        );

        jdbcTemplate.update(
            """
            INSERT INTO subscription_plan_versions (
                id,
                plan_id,
                version_number,
                audience_type,
                billing_period,
                price,
                quota_period_type,
                reservation_required,
                renewal_policy,
                suspension_policy
            )
            VALUES (
                ?, ?,
                1,
                'STUDENT',
                'MONTH',
                ?,
                'SUBSCRIPTION',
                FALSE,
                'MANUAL',
                'BLOCK_USAGE'
            )
            """,
            planVersionId,
            planId,
            snapshotPrice
        );

        jdbcTemplate.update(
            """
            INSERT INTO subscriptions (
                id,
                student_id,
                meal_beneficiary_id,
                plan_id,
                plan_version_id,
                status,
                starts_at,
                ends_at,
                payment_reference,
                administrative_payment_amount,
                activated_by,
                activated_at
            )
            VALUES (
                ?, ?,
                NULL,
                ?, ?,
                'ACTIVE',
                ?, ?, ?, ?,
                ?,
                CURRENT_TIMESTAMP
            )
            """,
            subscriptionId,
            selectedStudentId,
            planId,
            planVersionId,
            startsAt,
            endsAt,
            paymentReference,
            administrativeAmount,
            student.userId()
        );

        return new SubscriptionFixture(
            subscriptionId,
            planId,
            planVersionId
        );
    }

    private String suffix() {

        String raw =
            UUID.randomUUID()
                .toString()
                .replace(
                    "-",
                    ""
                );

        return raw.substring(
            0,
            12
        );
    }

    private record StudentFixture(
        UUID userId,
        UUID studentId
    ) {
    }

    private record SubscriptionFixture(
        UUID subscriptionId,
        UUID planId,
        UUID planVersionId
    ) {
    }
}
