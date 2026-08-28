package com.sup2i.food.notification;

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
class NotificationE2EIntegrationTest {

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
    private int timeSlotSequence = 0;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "NOTIF"
            );

        campusId =
            insertCampus(
                organizationId,
                "MAIN"
            );

        locationId =
            insertLocation(
                campusId,
                "SNACK"
            );
    }

    // =========================================================
    // 01 - NOTIFICATION CREATED ON QUEUED
    // =========================================================

    @Test
    void notificationCreatedWhenOrderQueued()
        throws Exception {

        Actor student =
            insertActor(
                "STU1",
                true
            );

        UUID orderId =
            payOrder(
                student,
                "PRD1"
            );

        String orderNumber =
            orderNumber(orderId);

        mockMvc.perform(
                get(
                    "/api/v1/notifications"
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
                jsonPath("$.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$[0].type")
                    .value("ORDER_QUEUED")
            )
            .andExpect(
                jsonPath("$[0].channel")
                    .value("PUSH")
            )
            .andExpect(
                jsonPath("$[0].status")
                    .value("PENDING")
            )
            .andExpect(
                jsonPath("$[0].title")
                    .value("Commande reçue")
            )
            .andExpect(
                jsonPath("$[0].body")
                    .value(
                        org.hamcrest.Matchers
                            .containsString(
                                orderNumber
                            )
                    )
            )
            .andExpect(
                jsonPath("$[0].readAt")
                    .doesNotExist()
            );
    }

    // =========================================================
    // 02 - NOTIFICATION CREATED ON READY
    // =========================================================

    @Test
    void notificationCreatedWhenOrderReady()
        throws Exception {

        Actor student =
            insertActor(
                "STU2",
                true
            );

        Actor staff =
            insertActor(
                "STAFF2",
                false
            );

        UUID orderId =
            payOrder(
                student,
                "PRD2"
            );

        UUID ticketId =
            kitchenTicketIdForOrder(
                orderId
            );

        mockMvc.perform(
                post(
                    "/api/v1/kitchen/tickets/{ticketId}/start-preparation",
                    ticketId
                )
                    .header(
                        "Authorization",
                        bearer(staff)
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                post(
                    "/api/v1/kitchen/tickets/{ticketId}/ready",
                    ticketId
                )
                    .header(
                        "Authorization",
                        bearer(staff)
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                get(
                    "/api/v1/notifications"
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
                jsonPath("$.length()")
                    .value(2)
            )
            .andExpect(
                jsonPath(
                    "$[?(@.type=='ORDER_READY')].title"
                )
                    .value("Commande prête")
            )
            .andExpect(
                jsonPath(
                    "$[?(@.type=='ORDER_QUEUED')].title"
                )
                    .value("Commande reçue")
            );
    }

    // =========================================================
    // 03 - NOTIFICATIONS SCOPED TO OWNER
    // =========================================================

    @Test
    void notificationsAreScopedToOwner()
        throws Exception {

        Actor studentA =
            insertActor(
                "STUA",
                true
            );

        Actor studentB =
            insertActor(
                "STUB",
                true
            );

        payOrder(
            studentA,
            "PRDA"
        );

        payOrder(
            studentB,
            "PRDB"
        );

        mockMvc.perform(
                get(
                    "/api/v1/notifications"
                )
                    .header(
                        "Authorization",
                        bearer(studentA)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(1)
            );

        mockMvc.perform(
                get(
                    "/api/v1/notifications"
                )
                    .header(
                        "Authorization",
                        bearer(studentB)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(1)
            );
    }

    // =========================================================
    // 04 - MARK AS READ IS IDEMPOTENT
    // =========================================================

    @Test
    void markReadIsIdempotent()
        throws Exception {

        Actor student =
            insertActor(
                "STU4",
                true
            );

        payOrder(
            student,
            "PRD4"
        );

        var listResult =
            mockMvc.perform(
                    get(
                        "/api/v1/notifications"
                    )
                        .header(
                            "Authorization",
                            bearer(student)
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn();

        String body =
            listResult.getResponse()
                .getContentAsString();

        java.util.regex.Matcher matcher =
            java.util.regex.Pattern.compile(
                    "\"id\":\"([0-9a-fA-F-]{36})\""
                )
                .matcher(body);

        org.assertj.core.api.Assertions
            .assertThat(
                matcher.find()
            )
            .isTrue();

        String notificationId =
            matcher.group(1);

        mockMvc.perform(
                post(
                    "/api/v1/notifications/{id}/read",
                    notificationId
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
                jsonPath(
                    "$.notification.status"
                )
                    .value("READ")
            )
            .andExpect(
                jsonPath(
                    "$.notification.readAt"
                )
                    .exists()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        mockMvc.perform(
                post(
                    "/api/v1/notifications/{id}/read",
                    notificationId
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
                jsonPath(
                    "$.notification.status"
                )
                    .value("READ")
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );
    }

    // =========================================================
    // 05 - SKIPPED WHEN TRANSACTIONAL PREFERENCE DISABLED
    // =========================================================

    @Test
    void notificationSkippedWhenTransactionalDisabled()
        throws Exception {

        Actor student =
            insertActor(
                "STU5",
                true
            );

        jdbcTemplate.update(
            """
            INSERT INTO notification_preferences (
                id,
                user_id,
                category,
                push_enabled
            )
            VALUES (?, ?, 'TRANSACTIONAL', FALSE)
            """,
            UUID.randomUUID(),
            student.userId()
        );

        payOrder(
            student,
            "PRD5"
        );

        mockMvc.perform(
                get(
                    "/api/v1/notifications"
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
                jsonPath("$.length()")
                    .value(0)
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

    private String orderNumber(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            "SELECT order_number FROM orders WHERE id = ?",
            String.class,
            orderId
        );
    }

    private UUID kitchenTicketIdForOrder(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            "SELECT id FROM kitchen_tickets WHERE order_id = ?",
            UUID.class,
            orderId
        );
    }

    private UUID payOrder(
        Actor student,
        String prefix
    ) throws Exception {

        UUID productId =
            insertProduct(
                prefix,
                "10.00"
            );

        UUID stockItemId =
            insertProductStockItem(
                productId
            );

        UUID stockLocationId =
            insertStockLocation(
                prefix
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "100.000"
        );

        UUID timeSlotId =
            insertTimeSlot();

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
                            productId
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
        UUID productId
    ) {

        return """
            {
              "locationId": "%s",
              "currency": "MAD",
              "customerNote": "Notification E2E",
              "timeSlotId": "%s",
              "items": [
                {
                  "productId": "%s",
                  "quantity": 1
                }
              ]
            }
            """.formatted(
                locationId,
                timeSlotId,
                productId
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
        String prefix
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
            VALUES (?, ?, ?, ?, TRUE)
            """,
            id,
            tenantId,
            prefix + " Campus",
            "C" + randomSuffix()
        );

        return id;
    }

    private UUID insertLocation(
        UUID selectedCampusId,
        String prefix
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
            VALUES (?, ?, ?, ?, 'SNACK', TRUE)
            """,
            id,
            selectedCampusId,
            prefix + " Location",
            "L" + randomSuffix()
        );

        return id;
    }

    private Actor insertActor(
        String prefix,
        boolean isStudent
    ) {

        UUID userId =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

        String email =
            "notif-"
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
            organizationId,
            email,
            "Notif",
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
                campusId,
                "STU-" + randomSuffix()
            );
        }

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "notification-e2e-"
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
            organizationId,
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
            organizationId,
            categoryId,
            prefix + "-" + randomSuffix(),
            prefix + " Product",
            new BigDecimal(price)
        );

        return productId;
    }

    private UUID insertStockLocation(
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
            locationId,
            prefix + " Stock"
        );

        return id;
    }

    private UUID insertProductStockItem(
        UUID productId
    ) {

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
            organizationId,
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

    private UUID insertTimeSlot() {

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
            locationId,
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
