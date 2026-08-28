package com.sup2i.food.subscription;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.security.service.AuthenticationTokens;
import com.sup2i.food.security.service.RefreshTokenService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.InetAddress;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SubscriptionE2EIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private UUID organizationId;
    private UUID campusId;
    private UUID locationId;

    private Actor student;
    private Actor plainStudent;
    private Actor admin;
    private Actor direction;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "SUB"
            );

        campusId =
            insertCampus(
                organizationId,
                "MAIN",
                true
            );

        locationId =
            insertLocation(
                campusId,
                "SNACK",
                "SNACK",
                true
            );

        student =
            insertStudentActor(
                organizationId,
                campusId,
                "STUA"
            );

        plainStudent =
            insertStudentActor(
                organizationId,
                campusId,
                "STUB"
            );

        admin =
            insertRoleActor(
                organizationId,
                "ADMIN",
                "ADMINISTRATION"
            );

        direction =
            insertRoleActor(
                organizationId,
                "DIR",
                "DIRECTION"
            );
    }

    // =========================================================
    // 01 - STUDENT SUBSCRIBES TO A NEW PLAN
    // =========================================================

    @Test
    void studentSubscribesToActivePlan() throws Exception {

        UUID planId =
            createPlan("MENU-MOIS");

        mockMvc.perform(
                post(
                    "/api/v1/subscriptions"
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "planId": "%s"
                        }
                        """.formatted(planId)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.subscription.status")
                    .value("PENDING")
            )
            .andExpect(
                jsonPath(
                    "$.subscription.entitlements[0].mealType"
                )
                    .value("LUNCH")
            )
            .andExpect(
                jsonPath(
                    "$.subscription.entitlements[0].totalQuota"
                )
                    .value(30)
            );
    }

    // =========================================================
    // 02 - OVERLAPPING SUBSCRIPTION REJECTED
    // =========================================================

    @Test
    void overlappingSubscriptionIsRejected() throws Exception {

        UUID planId =
            createPlan("PLAN-OVERLAP");

        UUID first =
            subscribe(planId, student);

        assertThat(first).isNotNull();

        mockMvc.perform(
                post(
                    "/api/v1/subscriptions"
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "planId": "%s"
                        }
                        """.formatted(planId)
                    )
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );
    }

    // =========================================================
    // 03 - ADMIN ACTIVATES SUSPENDS REACTIVATES CANCELS
    // =========================================================

    @Test
    void adminDrivesLifecycle() throws Exception {

        UUID planId =
            createPlan("LIFECYCLE");

        UUID subscriptionId =
            subscribe(planId, student);

        activate(
            subscriptionId,
            admin
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath(
                    "$.subscription.status"
                )
                    .value("ACTIVE")
            )
            .andExpect(
                jsonPath(
                    "$.subscription.paymentReference"
                )
                    .value("REF-001")
            );

        suspend(
            subscriptionId,
            admin,
            "Grève"
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath(
                    "$.subscription.status"
                )
                    .value("SUSPENDED")
            );

        reactivate(
            subscriptionId,
            admin,
            "Reprise"
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath(
                    "$.subscription.status"
                )
                    .value("ACTIVE")
            );

        cancel(
            subscriptionId,
            admin,
            "Demande étudiant"
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath(
                    "$.subscription.status"
                )
                    .value("CANCELLED")
            );

        assertThat(
            subscriptionStatus(subscriptionId)
        ).isEqualTo("CANCELLED");
    }

    // =========================================================
    // 04 - STUDENT CANCELLS OWN PENDING SUBSCRIPTION
    // =========================================================

    @Test
    void studentCancelsOwnPendingSubscription() throws Exception {

        UUID planId =
            createPlan("STUDENT-CANCEL");

        UUID subscriptionId =
            subscribe(planId, student);

        mockMvc.perform(
                post(
                    "/api/v1/subscriptions/{id}/cancel",
                    subscriptionId
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        "{}"
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath(
                    "$.subscription.status"
                )
                    .value("CANCELLED")
            );
    }

    // =========================================================
    // 05 - FOOD PASS ISSUE, REPLACEMENT, MY CARD
    // =========================================================

    @Test
    void foodPassIssueAndReplacement() throws Exception {

        String firstIssued =
            issueFoodPass(student.userId())
                .andExpect(
                    status().isOk()
                )
                .andExpect(
                    jsonPath("$.status")
                        .value("ACTIVE")
                )
                .andExpect(
                    jsonPath("$.qrToken")
                        .isNotEmpty()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID firstPassId =
            UUID.fromString(
                extractField(firstIssued, "id")
            );

        String firstCard =
            cardNumber(firstPassId);

        assertThat(firstCard).isNotNull();

        String secondIssued =
            issueFoodPass(student.userId())
                .andExpect(
                    status().isOk()
                )
                .andExpect(
                    jsonPath("$.status")
                        .value("ACTIVE")
                )
                .andExpect(
                    jsonPath("$.qrToken")
                        .isNotEmpty()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID secondPassId =
            UUID.fromString(
                extractField(secondIssued, "id")
            );

        String secondCard =
            cardNumber(secondPassId);

        assertThat(secondCard).isNotEqualTo(firstCard);

        assertThat(
            foodPassStatus(firstPassId)
        ).isEqualTo("REPLACED");

        mockMvc.perform(
                get(
                    "/api/v1/food-pass"
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("ACTIVE")
            )
            .andExpect(
                jsonPath("$.cardNumber")
                    .value(secondCard)
            );
    }

    // =========================================================
    // 06 - FOOD PASS BLOCK AND REACTIVATE
    // =========================================================

    @Test
    void foodPassBlockAndReactivate() throws Exception {

        UUID passId =
            issueFoodPassId(student.userId());

        blockFoodPass(passId)
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("BLOCKED")
            );

        assertThat(
            foodPassStatus(passId)
        ).isEqualTo("BLOCKED");

        reactivateFoodPass(passId)
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("ACTIVE")
            )
            .andExpect(
                jsonPath("$.qrToken")
                    .isNotEmpty()
            );
    }

    // =========================================================
    // 07 - MEAL CONSUMPTION (QUOTA AND DUPLICATE)
    // =========================================================

    @Test
    void mealConsumptionEnforcesDailyLimit() throws Exception {

        UUID planId =
            createPlan("CONSUME");

        UUID subscriptionId =
            subscribe(planId, student);

        activate(
            subscriptionId,
            admin
        )
            .andExpect(
                status().isOk()
            );

        UUID studentId =
            studentId(student);

        mockMvc.perform(
                post(
                    "/api/v1/admin/food-passes/consume"
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "studentId": "%s",
                          "mealType": "LUNCH"
                        }
                        """.formatted(studentId)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("VALID")
            )
            .andExpect(
                jsonPath("$.mealType")
                    .value("LUNCH")
            );

        assertThat(
            validUsageCount(studentId)
        ).isEqualTo(1);

        mockMvc.perform(
                post(
                    "/api/v1/admin/food-passes/consume"
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "studentId": "%s",
                          "mealType": "LUNCH"
                        }
                        """.formatted(studentId)
                    )
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );

        assertThat(
            validUsageCount(studentId)
        ).isEqualTo(1);
    }

    // =========================================================
    // 08 - RBAC: DIRECTION READ-ONLY, STUDENT DENIED
    // =========================================================

    @Test
    void rbacSeparatesReadFromWriteAndFromStudents() throws Exception {

        UUID planId =
            createPlan("RBAC");

        UUID subscriptionId =
            subscribe(planId, student);

        UUID passId =
            issueFoodPassId(student.userId());

        mockMvc.perform(
                get(
                    "/api/v1/admin/subscriptions"
                )
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/food-passes"
                )
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/subscriptions/{id}/activate",
                    subscriptionId
                )
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "paymentReference": "REF-X"
                        }
                        """
                    )
            )
            .andExpect(
                status().isForbidden()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("PERMISSION_DENIED")
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/food-passes/{id}/block",
                    passId
                )
                    .header(
                        "Authorization",
                        bearer(plainStudent)
                    )
            )
            .andExpect(
                status().isForbidden()
            );
    }

    // =========================================================
    // 09 - EXPIRED SUBSCRIPTIONS FLAGGED
    // =========================================================

    @Test
    void outdatedSubscriptionsAreExpired() throws Exception {

        UUID planId =
            createPlan("EXPIRY");

        UUID currentVersionId =
            currentVersionId(planId);

        UUID studentId =
            studentId(student);

        UUID subscriptionId =
            insertPastDueSubscription(
                studentId,
                planId,
                currentVersionId
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/subscriptions/expire-outdated"
                )
                    .header(
                        "Authorization",
                        bearer(admin)
                    )
            )
            .andExpect(
                status().isOk()
            );

        assertThat(
            subscriptionStatus(subscriptionId)
        ).isEqualTo("EXPIRED");
    }

    // =========================================================
    // HTTP HELPERS
    // =========================================================

    private UUID createPlan(
        String code
    ) throws Exception {

        org.springframework.test.web.servlet.MvcResult result =
            mockMvc.perform(
                    post(
                        "/api/v1/admin/subscription-plans"
                    )
                        .header(
                            "Authorization",
                            bearer(admin)
                        )
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            planJson(code)
                        )
                )
                .andReturn();

        assertThat(
            result.getResponse().getStatus()
        )
            .isEqualTo(200);

        String response =
            result.getResponse()
                .getContentAsString();

        assertThat(response)
            .contains("\"id\":\"");

        return UUID.fromString(
            extractField(response, "id")
        );
    }

    private String planJson(
        String code
    ) {

        return """
               {
                 "name": "Menu %s",
                 "code": "%s",
                 "billingPeriod": "MONTH",
                 "price": 10.00,
                 "services": ["LUNCH"],
                 "validityDays": 30,
                 "quotaValue": 30,
                 "maxPerDay": 1,
                 "reservationRequired": false,
                 "quotaPeriodType": "SUBSCRIPTION",
                 "renewalPolicy": "MANUAL",
                 "suspensionPolicy": "BLOCK_USAGE",
                 "audienceType": "STUDENT"
               }
               """
            .formatted(code, code);
    }

    private UUID subscribe(
        UUID planId,
        Actor requestActor
    ) throws Exception {

        String response =
            mockMvc.perform(
                    post(
                        "/api/v1/subscriptions"
                    )
                        .header(
                            "Authorization",
                            bearer(requestActor)
                        )
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            """
                            {
                              "planId": "%s"
                            }
                            """.formatted(planId)
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(
            extractField(response, "subscription.id")
        );
    }

    private org.springframework.test.web.servlet.ResultActions
        activate(
        UUID subscriptionId,
        Actor requestActor
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/admin/subscriptions/{id}/activate",
                subscriptionId
            )
                .header(
                    "Authorization",
                    bearer(requestActor)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "paymentReference": "REF-001",
                      "administrativePaymentAmount": 10.00
                    }
                    """
                )
        );
    }

    private org.springframework.test.web.servlet.ResultActions
        suspend(
        UUID subscriptionId,
        Actor requestActor,
        String reason
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/admin/subscriptions/{id}/suspend",
                subscriptionId
            )
                .header(
                    "Authorization",
                    bearer(requestActor)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "reason": "%s"
                    }
                    """.formatted(reason)
                )
        );
    }

    private org.springframework.test.web.servlet.ResultActions
        reactivate(
        UUID subscriptionId,
        Actor requestActor,
        String reason
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/admin/subscriptions/{id}/reactivate",
                subscriptionId
            )
                .header(
                    "Authorization",
                    bearer(requestActor)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "reason": "%s"
                    }
                    """.formatted(reason)
                )
        );
    }

    private org.springframework.test.web.servlet.ResultActions
        cancel(
        UUID subscriptionId,
        Actor requestActor,
        String reason
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/admin/subscriptions/{id}/cancel",
                subscriptionId
            )
                .header(
                    "Authorization",
                    bearer(requestActor)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "reason": "%s"
                    }
                    """.formatted(reason)
                )
        );
    }

    private org.springframework.test.web.servlet.ResultActions
        issueFoodPass(
        UUID studentId
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/admin/food-passes"
            )
                .header(
                    "Authorization",
                    bearer(admin)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "studentId": "%s"
                    }
                    """.formatted(studentId)
                )
        );
    }

    private UUID issueFoodPassId(
        UUID studentId
    ) throws Exception {

        String response =
            issueFoodPass(studentId)
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(
            extractField(response, "id")
        );
    }

    private org.springframework.test.web.servlet.ResultActions
        blockFoodPass(
        UUID foodPassId
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/admin/food-passes/{id}/block",
                foodPassId
            )
                .header(
                    "Authorization",
                    bearer(admin)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "reason": "Carte perdue"
                    }
                    """
                )
        );
    }

    private org.springframework.test.web.servlet.ResultActions
        reactivateFoodPass(
        UUID foodPassId
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/admin/food-passes/{id}/reactivate",
                foodPassId
            )
                .header(
                    "Authorization",
                    bearer(admin)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "reason": "Retrouvée"
                    }
                    """
                )
        );
    }

    private String bearer(
        Actor requestActor
    ) {
        return "Bearer "
            + requestActor.accessToken();
    }

    // =========================================================
    // TENANT / IDENTITY FIXTURES
    // =========================================================

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
            VALUES (?, ?, ?, TRUE)
            """,
            id,
            prefix + " Organization",
            prefix + randomSuffix()
        );

        return id;
    }

    private UUID insertCampus(
        UUID tenantId,
        String prefix,
        boolean active
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
                is_active
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            id,
            tenantId,
            prefix + " Campus",
            "C" + randomSuffix(),
            active
        );

        return id;
    }

    private UUID insertLocation(
        UUID selectedCampusId,
        String prefix,
        String type,
        boolean active
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
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            id,
            selectedCampusId,
            prefix + " Location",
            "L" + randomSuffix(),
            type,
            active
        );

        return id;
    }

    private Actor insertStudentActor(
        UUID tenantId,
        UUID selectedCampusId,
        String prefix
    ) {

        UUID userId =
            insertUser(
                tenantId,
                "sub-"
                    + prefix.toLowerCase()
                    + "-"
                    + randomSuffix()
                    + "@sup2i.test",
                prefix
            );

        UUID studentId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO students (
                id,
                user_id,
                campus_id,
                student_number,
                enrollment_status
            )
            VALUES (?, ?, ?, ?, 'ACTIVE')
            """,
            studentId,
            userId,
            selectedCampusId,
            "STU-" + randomSuffix()
        );

        return token(userId);
    }

    private Actor insertRoleActor(
        UUID tenantId,
        String prefix,
        String roleCode
    ) {

        UUID userId =
            insertUser(
                tenantId,
                "sub-"
                    + prefix.toLowerCase()
                    + "-"
                    + randomSuffix()
                    + "@sup2i.test",
                prefix
            );

        UUID roleId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM roles
                WHERE code = ?
                """,
                UUID.class,
                roleCode
            );

        jdbcTemplate.update(
            """
            INSERT INTO user_roles (
                id,
                user_id,
                role_id
            )
            VALUES (?, ?, ?)
            """,
            UUID.randomUUID(),
            userId,
            roleId
        );

        return token(userId);
    }

    private UUID insertUser(
        UUID tenantId,
        String email,
        String prefix
    ) {

        UUID userId =
            UUID.randomUUID();

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
            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """,
            userId,
            tenantId,
            email,
            "Sub",
            prefix
        );

        return userId;
    }

    private Actor token(
        UUID userId
    ) {

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "sub-e2e-"
                    + randomSuffix(),
                InetAddress
                    .getLoopbackAddress()
            );

        return new Actor(
            userId,
            tokens.accessToken()
        );
    }

    private UUID studentId(
        Actor requestActor
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM students
            WHERE user_id = ?
            """,
            UUID.class,
            requestActor.userId()
        );
    }

    private UUID currentVersionId(
        UUID planId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM subscription_plan_versions
            WHERE plan_id = ?
              AND effective_to IS NULL
            """,
            UUID.class,
            planId
        );
    }

    // =========================================================
    // DIRECT DATA FIXTURES
    // =========================================================

    private UUID insertPastDueSubscription(
        UUID studentId,
        UUID planId,
        UUID planVersionId
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO subscriptions (
                id,
                student_id,
                plan_id,
                plan_version_id,
                status,
                starts_at,
                ends_at
            )
            VALUES (
                ?, ?, ?, ?,
                'ACTIVE',
                CURRENT_DATE - 60,
                CURRENT_DATE - 31
            )
            """,
            id,
            studentId,
            planId,
            planVersionId
        );

        return id;
    }

    // =========================================================
    // DATABASE ASSERTION HELPERS
    // =========================================================

    private String subscriptionStatus(
        UUID subscriptionId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM subscriptions
            WHERE id = ?
            """,
            String.class,
            subscriptionId
        );
    }

    private String foodPassStatus(
        UUID foodPassId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM food_passes
            WHERE id = ?
            """,
            String.class,
            foodPassId
        );
    }

    private String cardNumber(
        UUID foodPassId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT card_number
            FROM food_passes
            WHERE id = ?
            """,
            String.class,
            foodPassId
        );
    }

    private long validUsageCount(
        UUID studentId
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM meal_usages
                WHERE student_id = ?
                  AND status = 'VALID'
                """,
                Long.class,
                studentId
            );

        return count == null
            ? 0L
            : count;
    }

    private String randomSuffix() {

        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10);
    }

    private String extractField(
        String json,
        String path
    ) {

        try {

            com.fasterxml.jackson.databind.JsonNode node =
                JSON.readTree(json);

            for (
                String segment
                : path.split("\\.")
            ) {

                node =
                    node.get(segment);

                if (
                    node == null
                ) {
                    throw new IllegalStateException(
                        "Field "
                            + path
                            + " not found in: "
                            + json
                    );
                }
            }

            return node.asText();

        } catch (
            java.io.IOException exception
        ) {
            throw new IllegalStateException(
                "Could not parse JSON: "
                    + json,
                exception
            );
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper
        JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private record Actor(
        UUID userId,
        String accessToken
    ) {
    }
}