package com.sup2i.food.qr;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
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
class ScanE2EIntegrationTest {

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
    private UUID timeSlotId;

    private Actor actor;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "SCAN"
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

        timeSlotId =
            insertTimeSlot(
                locationId,
                1000
            );

        actor =
            insertActor(
                organizationId,
                campusId,
                "MAIN"
            );
    }

    // =========================================================
    // 01 - VALID QR RESOLVES TO REAL ORDER STATE
    // =========================================================

    @Test
    void validQrResolvesToOrderWithRealStatus()
        throws Exception {

        AwaitingOrder order =
            awaitingPaidOrder(
                "VALID",
                2
            );

        resolve(
            order.qrToken()
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.type")
                    .value("ORDER")
            )
            .andExpect(
                jsonPath("$.referenceId")
                    .value(
                        order.orderId()
                            .toString()
                    )
            )
            .andExpect(
                jsonPath("$.allowedActions")
                    .isArray()
            )
            .andExpect(
                jsonPath("$.allowedActions[0]")
                    .value("PAY")
            )
            .andExpect(
                jsonPath("$.details.orderStatus")
                    .value("AWAITING_PAYMENT")
            )
            .andExpect(
                jsonPath("$.details.orderNumber")
                    .value(
                        order.orderNumber()
                    )
            )
            .andExpect(
                jsonPath("$.details.total")
                    .value(20.00)
            );
    }

    // =========================================================
    // 02 - EXPIRED QR
    // =========================================================

    @Test
    void expiredQrIsRejectedCleanly()
        throws Exception {

        AwaitingOrder order =
            awaitingPaidOrder(
                "EXPIRE",
                1
            );

        jdbcTemplate.update(
            """
            UPDATE qr_credentials
            SET expires_at =
                issued_at
                + INTERVAL '1 millisecond'
            WHERE subject_id = ?
            """,
            order.orderId()
        );

        resolve(
            order.qrToken()
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
    // 03 - REVOKED QR
    // =========================================================

    @Test
    void revokedQrIsRejectedCleanly()
        throws Exception {

        AwaitingOrder order =
            awaitingPaidOrder(
                "REVOKE",
                1
            );

        jdbcTemplate.update(
            """
            UPDATE qr_credentials
            SET status = 'REVOKED',
                revoked_at = CURRENT_TIMESTAMP
            WHERE subject_id = ?
            """,
            order.orderId()
        );

        resolve(
            order.qrToken()
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
    // 04 - ALREADY USED (SINGLE USE)
    // =========================================================

    @Test
    void secondResolveOfSameQrIsRejected()
        throws Exception {

        AwaitingOrder order =
            awaitingPaidOrder(
                "REUSE",
                1
            );

        resolve(
            order.qrToken()
        )
            .andExpect(
                status().isOk()
            );

        assertThat(
            credentialStatus(
                order.orderId()
            )
        ).isEqualTo(
            "USED"
        );

        resolve(
            order.qrToken()
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
    // 05 - FORGED / UNKNOWN TOKEN
    // =========================================================

    @Test
    void forgedTokenIsRejectedWithoutDetail()
        throws Exception {

        resolve(
            "this-token-was-never-issued-by-the-server"
        )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("QR code is invalid.")
            );
    }

    // =========================================================
    // 06 - NEVER CLAIMS PAYMENT WITHOUT REAL ORDER STATUS
    // =========================================================

    @Test
    void resolveReflectsRealOrderStatusEvenWhenNoLongerAwaitingPayment()
        throws Exception {

        AwaitingOrder order =
            awaitingPaidOrder(
                "CANCELLED",
                1
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/cancel",
                    order.orderId()
                )
                    .header(
                        "Authorization",
                        bearer(actor)
                    )
            )
            .andExpect(
                status().isOk()
            );

        assertThat(
            orderStatus(
                order.orderId()
            )
        ).isEqualTo(
            "CANCELLED"
        );

        resolve(
            order.qrToken()
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.details.orderStatus")
                    .value("CANCELLED")
            )
            .andExpect(
                jsonPath("$.allowedActions")
                    .isArray()
            )
            .andExpect(
                jsonPath("$.allowedActions.length()")
                    .value(0)
            );
    }

    // =========================================================
    // HTTP HELPERS
    // =========================================================

    private ResultActions resolve(
        String token
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/scans/resolve"
            )
                .header(
                    "Authorization",
                    bearer(actor)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "token": "%s"
                    }
                    """.formatted(
                        token
                    )
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
    // ORDER FIXTURE (draft -> submit -> begin-payment)
    // =========================================================

    private AwaitingOrder awaitingPaidOrder(
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
                        bearer(actor)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "locationId": "%s",
                          "currency": "MAD",
                          "customerNote": "Scan E2E",
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
                        bearer(actor)
                    )
            )
            .andExpect(
                status().isOk()
            );

        MvcResult result =
            mockMvc.perform(
                    post(
                        "/api/v1/orders/{orderId}/begin-payment",
                        orderId
                    )
                        .header(
                            "Authorization",
                            bearer(actor)
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn();

        String body =
            result.getResponse()
                .getContentAsString();

        String qrToken =
            extractJsonString(
                body,
                "qrToken"
            );

        String orderNumber =
            extractJsonString(
                body,
                "orderNumber"
            );

        assertThat(qrToken)
            .isNotBlank();

        return new AwaitingOrder(
            orderId,
            qrToken,
            orderNumber
        );
    }

    private String extractJsonString(
        String json,
        String field
    ) {

        Pattern pattern =
            Pattern.compile(
                "\"" + field + "\":\"([^\"]*)\""
            );

        Matcher matcher =
            pattern.matcher(json);

        if (!matcher.find()) {
            throw new IllegalStateException(
                "Field "
                    + field
                    + " not found in response body."
            );
        }

        return matcher.group(1);
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

    private UUID insertTimeSlot(
        UUID selectedLocationId,
        int capacity
    ) {

        UUID id =
            UUID.randomUUID();

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
                '12:00', '12:15',
                ?, 0
            )
            """,
            id,
            selectedLocationId,
            capacity
        );

        return id;
    }

    private Actor insertActor(
        UUID tenantId,
        UUID selectedCampusId,
        String prefix
    ) {

        UUID userId =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

        String email =
            "scan-"
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
            "Scan",
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

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "scan-e2e-"
                    + prefix,
                InetAddress
                    .getLoopbackAddress()
            );

        return new Actor(
            userId,
            studentId,
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

    private String credentialStatus(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM qr_credentials
            WHERE subject_id = ?
              AND credential_type = 'ORDER'
            """,
            String.class,
            orderId
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
        UUID studentId,
        String accessToken
    ) {
    }

    private record AwaitingOrder(
        UUID orderId,
        String qrToken,
        String orderNumber
    ) {
    }
}
