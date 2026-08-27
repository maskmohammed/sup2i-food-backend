package com.sup2i.food.kitchen;

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
import org.springframework.test.web.servlet.ResultActions;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
class KitchenE2EIntegrationTest {

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
    private Actor cook;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "KDS"
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
            insertActor(
                organizationId,
                campusId,
                "STUDENT",
                true
            );

        cook =
            insertActor(
                organizationId,
                campusId,
                "COOK",
                false
            );
    }

    // =========================================================
    // 01 - TICKET CREATED AUTOMATICALLY AND ONLY ON PAY()
    // =========================================================

    @Test
    void ticketCreatedAutomaticallyAndOnlyOnSuccessfulPay()
        throws Exception {

        UUID orderId =
            awaitingPaymentOrder(
                "AUTO",
                2
            );

        assertThat(
            kitchenTicketCount(
                orderId
            )
        ).isZero();

        pay(
            orderId,
            student
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.order.status")
                    .value("QUEUED")
            );

        assertThat(
            kitchenTicketCount(
                orderId
            )
        ).isEqualTo(1L);

        assertThat(
            orderStatus(
                orderId
            )
        ).isEqualTo(
            "QUEUED"
        );

        UUID ticketId =
            kitchenTicketId(
                orderId
            );

        assertThat(
            kitchenTicketStatus(
                ticketId
            )
        ).isEqualTo(
            "QUEUED"
        );

        assertThat(
            kitchenTicketItemCount(
                ticketId
            )
        ).isEqualTo(1L);
    }

    // =========================================================
    // 02 - WORKFLOW PROGRESSES IN EXPECTED ORDER + ORDER SYNC
    // =========================================================

    @Test
    void workflowProgressesInExpectedOrderAndSyncsOrder()
        throws Exception {

        UUID orderId =
            paidOrder(
                "FLOW",
                1
            );

        UUID ticketId =
            kitchenTicketId(
                orderId
            );

        startPreparation(
            ticketId,
            cook
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.ticket.status")
                    .value("PREPARING")
            );

        assertThat(
            kitchenTicketStatus(
                ticketId
            )
        ).isEqualTo(
            "PREPARING"
        );

        assertThat(
            kitchenTicketAcceptedAt(
                ticketId
            )
        ).isNotNull();

        assertThat(
            kitchenTicketStartedAt(
                ticketId
            )
        ).isNotNull();

        assertThat(
            orderStatus(
                orderId
            )
        ).isEqualTo(
            "PREPARING"
        );

        ready(
            ticketId,
            cook
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.ticket.status")
                    .value("READY")
            );

        assertThat(
            kitchenTicketStatus(
                ticketId
            )
        ).isEqualTo(
            "READY"
        );

        assertThat(
            orderStatus(
                orderId
            )
        ).isEqualTo(
            "READY"
        );

        assertThat(
            orderReadyAt(
                orderId
            )
        ).isNotNull();
    }

    // =========================================================
    // 03 - SHORTCUT REJECTED (READY WITHOUT PREPARING)
    // =========================================================

    @Test
    void readyDirectlyFromQueuedIsRejected()
        throws Exception {

        UUID orderId =
            paidOrder(
                "SHORTCUT",
                1
            );

        UUID ticketId =
            kitchenTicketId(
                orderId
            );

        ready(
            ticketId,
            cook
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );

        assertThat(
            kitchenTicketStatus(
                ticketId
            )
        ).isEqualTo(
            "QUEUED"
        );

        assertThat(
            orderStatus(
                orderId
            )
        ).isEqualTo(
            "QUEUED"
        );
    }

    // =========================================================
    // 04 - IDEMPOTENCY
    // =========================================================

    @Test
    void transitionsAreIdempotentOnReplay()
        throws Exception {

        UUID orderId =
            paidOrder(
                "REPLAY",
                1
            );

        UUID ticketId =
            kitchenTicketId(
                orderId
            );

        startPreparation(
            ticketId,
            cook
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        startPreparation(
            ticketId,
            cook
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        assertThat(
            historyCount(
                orderId
            )
        ).isEqualTo(6L);

        ready(
            ticketId,
            cook
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        ready(
            ticketId,
            cook
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        assertThat(
            historyCount(
                orderId
            )
        ).isEqualTo(7L);
    }

    // =========================================================
    // 05 - QUEUE LISTING
    // =========================================================

    @Test
    void queueListsActiveTicketsForOrganization()
        throws Exception {

        UUID orderId =
            paidOrder(
                "QUEUE",
                1
            );

        mockMvc.perform(
                get(
                    "/api/v1/kitchen/queue"
                )
                    .header(
                        "Authorization",
                        bearer(cook)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$[?(@.orderId=='"
                    + orderId
                    + "')]")
                    .exists()
            );
    }

    // =========================================================
    // HTTP HELPERS
    // =========================================================

    private ResultActions pay(
        UUID orderId,
        Actor requestActor
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/orders/{orderId}/pay",
                orderId
            )
                .header(
                    "Authorization",
                    bearer(requestActor)
                )
        );
    }

    private ResultActions startPreparation(
        UUID ticketId,
        Actor requestActor
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/kitchen/tickets/{ticketId}/start-preparation",
                ticketId
            )
                .header(
                    "Authorization",
                    bearer(requestActor)
                )
        );
    }

    private ResultActions ready(
        UUID ticketId,
        Actor requestActor
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/kitchen/tickets/{ticketId}/ready",
                ticketId
            )
                .header(
                    "Authorization",
                    bearer(requestActor)
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
    // ORDER FIXTURES (draft -> submit -> begin-payment [-> pay])
    // =========================================================

    private UUID awaitingPaymentOrder(
        String prefix,
        int quantity
    ) throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                prefix,
                "10.00"
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
            "20.000"
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
                        """
                        {
                          "locationId": "%s",
                          "currency": "MAD",
                          "customerNote": "Kitchen E2E",
                          "items": [
                            {
                              "productId": "%s",
                              "quantity": %d
                            }
                          ]
                        }
                        """.formatted(
                            locationId,
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

        return orderId;
    }

    private UUID paidOrder(
        String prefix,
        int quantity
    ) throws Exception {

        UUID orderId =
            awaitingPaymentOrder(
                prefix,
                quantity
            );

        pay(
            orderId,
            student
        )
            .andExpect(
                status().isOk()
            );

        return orderId;
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

    private Actor insertActor(
        UUID tenantId,
        UUID selectedCampusId,
        String prefix,
        boolean isStudent
    ) {

        UUID userId =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

        String email =
            "kitchen-"
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
            "Kitchen",
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

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "kitchen-e2e-"
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
    // CATALOG / INVENTORY FIXTURES
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

    // =========================================================
    // DATABASE ASSERTION HELPERS
    // =========================================================

    private String orderStatus(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM orders
            WHERE id = ?
            """,
            String.class,
            orderId
        );
    }

    private Object orderReadyAt(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT ready_at
            FROM orders
            WHERE id = ?
            """,
            java.sql.Timestamp.class,
            orderId
        );
    }

    private Long historyCount(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM order_status_history
            WHERE order_id = ?
            """,
            Long.class,
            orderId
        );
    }

    private Long kitchenTicketCount(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM kitchen_tickets
            WHERE order_id = ?
            """,
            Long.class,
            orderId
        );
    }

    private UUID kitchenTicketId(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM kitchen_tickets
            WHERE order_id = ?
            """,
            UUID.class,
            orderId
        );
    }

    private String kitchenTicketStatus(
        UUID ticketId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM kitchen_tickets
            WHERE id = ?
            """,
            String.class,
            ticketId
        );
    }

    private Object kitchenTicketAcceptedAt(
        UUID ticketId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT accepted_at
            FROM kitchen_tickets
            WHERE id = ?
            """,
            java.sql.Timestamp.class,
            ticketId
        );
    }

    private Object kitchenTicketStartedAt(
        UUID ticketId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT started_at
            FROM kitchen_tickets
            WHERE id = ?
            """,
            java.sql.Timestamp.class,
            ticketId
        );
    }

    private Long kitchenTicketItemCount(
        UUID ticketId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM kitchen_ticket_items
            WHERE kitchen_ticket_id = ?
            """,
            Long.class,
            ticketId
        );
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
