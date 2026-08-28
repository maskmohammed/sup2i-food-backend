package com.sup2i.food.dashboard;

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

import java.math.BigDecimal;
import java.net.InetAddress;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class DashboardE2EIntegrationTest {

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

    private Actor directionActor;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "DASH"
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

        directionActor =
            insertActor(
                organizationId,
                campusId,
                "DIR",
                false,
                true
            );
    }

    // =========================================================
    // 01 - ROLE PROTECTION
    // =========================================================

    @Test
    void dashboardRequiresDirectionRole()
        throws Exception {

        Actor student =
            insertActor(
                organizationId,
                campusId,
                "STU",
                true,
                false
            );

        mockMvc.perform(
                get(
                    "/api/v1/dashboard/summary"
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                get(
                    "/api/v1/dashboard/summary"
                )
                    .header(
                        "Authorization",
                        bearer(directionActor)
                    )
            )
            .andExpect(
                status().isOk()
            );
    }

    // =========================================================
    // 02 - REVENUE AND AVERAGE BASKET EXACT AMOUNTS
    // =========================================================

    @Test
    void revenueAndAverageBasketMatchExactAmounts()
        throws Exception {

        payOrder("REV1", "20.00", 1);
        payOrder("REV2", "30.00", 1);
        payOrder("REV3", "40.00", 1);

        mockMvc.perform(
                get(
                    "/api/v1/dashboard/summary"
                )
                    .header(
                        "Authorization",
                        bearer(directionActor)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.revenue.today")
                    .value(90.00)
            )
            .andExpect(
                jsonPath("$.revenue.thisWeek")
                    .value(90.00)
            )
            .andExpect(
                jsonPath("$.revenue.thisMonth")
                    .value(90.00)
            )
            .andExpect(
                jsonPath("$.averageBasket")
                    .value(30.00)
            );
    }

    // =========================================================
    // 03 - REVENUE EXCLUDES PAYMENTS OUTSIDE THE PERIOD
    // =========================================================

    @Test
    void revenueExcludesPaymentsOutsideThePeriod()
        throws Exception {

        payOrder("INPERIOD", "50.00", 1);

        UUID oldOrderId =
            payOrder("OLDPAY", "999.00", 1);

        jdbcTemplate.update(
            """
            UPDATE payments
            SET paid_at =
                CURRENT_TIMESTAMP - INTERVAL '40 days'
            WHERE order_id = ?
            """,
            oldOrderId
        );

        mockMvc.perform(
                get(
                    "/api/v1/dashboard/summary"
                )
                    .header(
                        "Authorization",
                        bearer(directionActor)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.revenue.today")
                    .value(50.00)
            )
            .andExpect(
                jsonPath("$.revenue.thisWeek")
                    .value(50.00)
            )
            .andExpect(
                jsonPath("$.revenue.thisMonth")
                    .value(50.00)
            );
    }

    // =========================================================
    // 04 - ORDER STATUS BREAKDOWN
    // =========================================================

    @Test
    void orderStatusBreakdownReflectsRealCounts()
        throws Exception {

        draftOrder("DRAFT1");
        draftOrder("DRAFT2");
        submittedOrder("CREATED1");
        payOrder("QUEUED1", "10.00", 1);

        mockMvc.perform(
                get(
                    "/api/v1/dashboard/summary"
                )
                    .header(
                        "Authorization",
                        bearer(directionActor)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath(
                    "$.ordersByStatus[?(@.status=='DRAFT')].count"
                )
                    .value(2)
            )
            .andExpect(
                jsonPath(
                    "$.ordersByStatus[?(@.status=='CREATED')].count"
                )
                    .value(1)
            )
            .andExpect(
                jsonPath(
                    "$.ordersByStatus[?(@.status=='QUEUED')].count"
                )
                    .value(1)
            );
    }

    // =========================================================
    // 05 - TOP PRODUCTS EXACT QUANTITIES
    // =========================================================

    @Test
    void topProductsReflectExactQuantities()
        throws Exception {

        UUID productA =
            insertProduct(
                organizationId,
                "POPULAR",
                "10.00"
            );

        UUID productB =
            insertProduct(
                organizationId,
                "RARE",
                "10.00"
            );

        payOrderForProduct(
            "TOP1",
            productA,
            5
        );

        payOrderForProduct(
            "TOP2",
            productA,
            3
        );

        payOrderForProduct(
            "TOP3",
            productB,
            1
        );

        mockMvc.perform(
                get(
                    "/api/v1/dashboard/summary"
                )
                    .header(
                        "Authorization",
                        bearer(directionActor)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath(
                    "$.topProducts[?(@.productId=='"
                        + productA
                        + "')].quantitySold"
                )
                    .value(8)
            )
            .andExpect(
                jsonPath(
                    "$.topProducts[?(@.productId=='"
                        + productB
                        + "')].quantitySold"
                )
                    .value(1)
            );
    }

    // =========================================================
    // 06 - AVERAGE PREPARATION TIME
    // =========================================================

    @Test
    void averagePreparationTimeMatchesKnownDuration()
        throws Exception {

        UUID orderId =
            payOrder(
                "PREP",
                "10.00",
                1
            );

        jdbcTemplate.update(
            """
            UPDATE kitchen_tickets
            SET started_at = CURRENT_TIMESTAMP - INTERVAL '10 minutes',
                ready_at = CURRENT_TIMESTAMP
            WHERE order_id = ?
            """,
            orderId
        );

        var result =
            mockMvc.perform(
                    get(
                        "/api/v1/dashboard/summary"
                    )
                        .header(
                            "Authorization",
                            bearer(directionActor)
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn();

        String body =
            result.getResponse()
                .getContentAsString();

        java.util.regex.Matcher matcher =
            java.util.regex.Pattern.compile(
                    "\"averagePreparationMinutes\":([0-9.]+)"
                )
                .matcher(body);

        assertThat(
            matcher.find()
        ).isTrue();

        double minutes =
            Double.parseDouble(
                matcher.group(1)
            );

        assertThat(minutes)
            .isCloseTo(
                10.0,
                within(0.05)
            );
    }

    // =========================================================
    // HTTP / ORDER FLOW HELPERS
    // =========================================================

    private String bearer(
        Actor requestActor
    ) {

        return "Bearer "
            + requestActor.accessToken();
    }

    private UUID draftOrder(
        String prefix
    ) throws Exception {

        Actor student =
            insertActor(
                organizationId,
                campusId,
                prefix,
                true,
                false
            );

        UUID productId =
            insertProduct(
                organizationId,
                prefix,
                "10.00"
            );

        UUID timeSlotId =
            insertTimeSlot(
                locationId
            );

        UUID orderId =
            UUID.randomUUID();

        mockMvc.perform(
                put(
                    "/api/v1/orders/{orderId}",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        draftJson(
                            timeSlotId,
                            productId,
                            1
                        )
                    )
            )
            .andExpect(
                status().isOk()
            );

        return orderId;
    }

    private UUID submittedOrder(
        String prefix
    ) throws Exception {

        Actor student =
            insertActor(
                organizationId,
                campusId,
                prefix,
                true,
                false
            );

        UUID productId =
            insertProduct(
                organizationId,
                prefix,
                "10.00"
            );

        UUID timeSlotId =
            insertTimeSlot(
                locationId
            );

        UUID orderId =
            UUID.randomUUID();

        mockMvc.perform(
                put(
                    "/api/v1/orders/{orderId}",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        draftJson(
                            timeSlotId,
                            productId,
                            1
                        )
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/submit",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isOk()
            );

        return orderId;
    }

    private UUID payOrder(
        String prefix,
        String price,
        int quantity
    ) throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                prefix,
                price
            );

        return payOrderForProductInternal(
            prefix,
            productId,
            quantity
        );
    }

    private UUID payOrderForProduct(
        String prefix,
        UUID productId,
        int quantity
    ) throws Exception {

        return payOrderForProductInternal(
            prefix,
            productId,
            quantity
        );
    }

    private UUID payOrderForProductInternal(
        String prefix,
        UUID productId,
        int quantity
    ) throws Exception {

        Actor student =
            insertActor(
                organizationId,
                campusId,
                prefix,
                true,
                false
            );

        UUID stockItemId =
            insertProductStockItem(
                organizationId,
                productId
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                prefix
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "100.000"
        );

        UUID timeSlotId =
            insertTimeSlot(
                locationId
            );

        UUID orderId =
            UUID.randomUUID();

        mockMvc.perform(
                put(
                    "/api/v1/orders/{orderId}",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        draftJson(
                            timeSlotId,
                            productId,
                            quantity
                        )
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/submit",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/begin-payment",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/pay",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isOk()
            );

        return orderId;
    }

    private String draftJson(
        UUID timeSlotId,
        UUID productId,
        int quantity
    ) {

        return """
            {
              "locationId": "%s",
              "currency": "MAD",
              "customerNote": "Dashboard E2E",
              "timeSlotId": "%s",
              "items": [
                {
                  "productId": "%s",
                  "quantity": %d
                }
              ]
            }
            """.formatted(
                locationId,
                timeSlotId,
                productId,
                quantity
            );
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

    /**
     * isStudent creates a students row (needed for the order lifecycle).
     * withDirectionRole assigns the DIRECTION role seeded by V060.
     */
    private Actor insertActor(
        UUID tenantId,
        UUID selectedCampusId,
        String prefix,
        boolean isStudent,
        boolean withDirectionRole
    ) {

        UUID userId =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

        String email =
            "dash-"
                + prefix.toLowerCase()
                + "-"
                + suffix
                + "@sup2i.test";

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
            "Dashboard",
            prefix
        );

        if (isStudent) {

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
        }

        if (withDirectionRole) {

            UUID directionRoleId =
                jdbcTemplate.queryForObject(
                    """
                    SELECT id
                    FROM roles
                    WHERE code = 'DIRECTION'
                    """,
                    UUID.class
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
                directionRoleId
            );
        }

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "dashboard-e2e-"
                    + prefix,
                InetAddress
                    .getLoopbackAddress()
            );

        return new Actor(
            userId,
            tokens.accessToken()
        );
    }

    // =========================================================
    // CATALOG / INVENTORY / TIME SLOT FIXTURES
    // =========================================================

    private UUID insertProduct(
        UUID tenantId,
        String prefix,
        String price
    ) {

        UUID categoryId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO categories (
                id,
                organization_id,
                name,
                slug,
                display_order,
                is_active
            )
            VALUES (?, ?, ?, ?, 0, TRUE)
            """,
            categoryId,
            tenantId,
            prefix + " Category",
            "category-" + randomSuffix()
        );

        UUID productId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO products (
                id,
                organization_id,
                category_id,
                sku,
                name,
                product_type,
                base_price,
                tax_rate,
                preparation_minutes,
                track_stock,
                is_prepared,
                is_active
            )
            VALUES (
                ?, ?, ?, ?, ?, 'PACKAGED',
                ?, 0.00, 0,
                TRUE, FALSE, TRUE
            )
            """,
            productId,
            tenantId,
            categoryId,
            prefix + "-" + randomSuffix(),
            prefix + " Product",
            new BigDecimal(price)
        );

        return productId;
    }

    private UUID insertStockLocation(
        UUID selectedLocationId,
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_locations (
                id,
                location_id,
                name,
                type,
                is_active
            )
            VALUES (?, ?, ?, 'STORAGE', TRUE)
            """,
            id,
            selectedLocationId,
            prefix + " Stock"
        );

        return id;
    }

    private UUID insertProductStockItem(
        UUID tenantId,
        UUID productId
    ) {

        java.util.List<UUID> existing =
            jdbcTemplate.query(
                """
                SELECT id
                FROM stock_items
                WHERE organization_id = ?
                  AND product_id = ?
                """,
                (resultSet, rowNum) ->
                    UUID.fromString(
                        resultSet.getString(
                            "id"
                        )
                    ),
                tenantId,
                productId
            );

        if (!existing.isEmpty()) {

            return existing.get(0);
        }

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_items (
                id,
                organization_id,
                product_id,
                base_unit,
                track_expiry
            )
            VALUES (?, ?, ?, 'PIECE', FALSE)
            """,
            id,
            tenantId,
            productId
        );

        return id;
    }

    private void insertBalance(
        UUID stockItemId,
        UUID stockLocationId,
        String physical
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO stock_balances (
                stock_item_id,
                stock_location_id,
                physical_quantity,
                reserved_quantity
            )
            VALUES (?, ?, ?, 0.000)
            """,
            stockItemId,
            stockLocationId,
            new BigDecimal(physical)
        );
    }

    private int timeSlotSequence = 0;

    private UUID insertTimeSlot(
        UUID selectedLocationId
    ) {

        UUID id =
            UUID.randomUUID();

        int minuteOffset =
            (timeSlotSequence++) * 5;

        String startTime =
            "%02d:%02d".formatted(
                10,
                minuteOffset
            );

        String endTime =
            "%02d:%02d".formatted(
                10,
                minuteOffset + 4
            );

        jdbcTemplate.update(
            """
            INSERT INTO time_slots (
                id,
                location_id,
                slot_date,
                start_time,
                end_time,
                capacity,
                reserved_count
            )
            VALUES (
                ?, ?,
                CURRENT_DATE + 1,
                ?::time, ?::time,
                1000, 0
            )
            """,
            id,
            selectedLocationId,
            startTime,
            endTime
        );

        return id;
    }

    private String randomSuffix() {

        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10);
    }

    private record Actor(
        UUID userId,
        String accessToken
    ) {
    }
}
