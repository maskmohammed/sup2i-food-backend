package com.sup2i.food.inventory;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.security.config.SecurityProperties;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
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
class InventoryCountE2EIntegrationTest {

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

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private SecurityProperties securityProperties;

    private UUID organizationId;
    private UUID userId;
    private String email;
    private String sessionId;

    @BeforeEach
    void seedTenantAndSession() {

        String suffix =
            randomSuffix();

        organizationId =
            UUID.randomUUID();

        userId =
            UUID.randomUUID();

        email =
            "inventory-count-"
                + suffix
                + "@sup2i.test";

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
            organizationId,
            "Inventory Count " + suffix,
            "IC" + suffix
        );

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
            "Inventory",
            "Counter"
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "inventory-count-e2e",
                InetAddress.getLoopbackAddress()
            );

        Jwt jwt =
            jwtDecoder.decode(
                tokens.accessToken()
            );

        sessionId =
            jwt.getClaimAsString(
                "sid"
            );
    }

    // =========================================================
    // SECURITY / SESSION CREATION
    // =========================================================

    @Test
    void inventorySessionRequiresProductWritePermission()
        throws Exception {

        String body =
            """
            {
              "stockLocationId": "%s",
              "notes": "RBAC"
            }
            """.formatted(
                UUID.randomUUID()
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/sessions/{sessionId}",
                    UUID.randomUUID()
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(body)
            )
            .andExpect(
                status().isForbidden()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("PERMISSION_DENIED")
            );
    }

    @Test
    void createSessionIsIdempotentAndDifferentPayloadConflicts()
        throws Exception {

        UUID stockLocationId =
            insertOwnedStockLocation(
                "IDEMPOTENT",
                true
            );

        UUID inventorySessionId =
            UUID.randomUUID();

        String body =
            sessionBody(
                stockLocationId,
                "Count A"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/sessions/{sessionId}",
                    inventorySessionId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(body)
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.session.status")
                    .value("OPEN")
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/sessions/{sessionId}",
                    inventorySessionId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(body)
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/sessions/{sessionId}",
                    inventorySessionId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        sessionBody(
                            stockLocationId,
                            "Different payload"
                        )
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
            sessionCount(
                inventorySessionId
            )
        ).isEqualTo(1L);
    }

    @Test
    void onlyOneActiveSessionExistsPerStockLocation()
        throws Exception {

        UUID stockLocationId =
            insertOwnedStockLocation(
                "SINGLE-ACTIVE",
                true
            );

        UUID first =
            UUID.randomUUID();

        UUID second =
            UUID.randomUUID();

        createSession(
            first,
            stockLocationId
        );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/sessions/{sessionId}",
                    second
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        sessionBody(
                            stockLocationId,
                            null
                        )
                    )
            )
            .andExpect(
                status().isConflict()
            );

        cancelSession(first)
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/sessions/{sessionId}",
                    second
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        sessionBody(
                            stockLocationId,
                            null
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );
    }

    @Test
    void sessionRejectsForeignAndInactiveStockLocations()
        throws Exception {

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-COUNT"
            );

        UUID foreignLocation =
            insertStockLocationForOrganization(
                foreignOrganization,
                "FOREIGN",
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/sessions/{sessionId}",
                    UUID.randomUUID()
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        sessionBody(
                            foreignLocation,
                            null
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            );

        UUID inactive =
            insertOwnedStockLocation(
                "INACTIVE",
                false
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/sessions/{sessionId}",
                    UUID.randomUUID()
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        sessionBody(
                            inactive,
                            null
                        )
                    )
            )
            .andExpect(
                status().isBadRequest()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_ERROR")
            );
    }

    // =========================================================
    // COUNTING
    // =========================================================

    @Test
    void countCapturesSnapshotAndRecountPreservesOriginalSnapshot()
        throws Exception {

        UUID stockItemId =
            insertOwnedStockItem(
                "SNAPSHOT"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "SNAPSHOT",
                true
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "10.000",
            "3.000"
        );

        UUID inventorySessionId =
            UUID.randomUUID();

        createSession(
            inventorySessionId,
            stockLocationId
        );

        countItem(
            inventorySessionId,
            stockItemId,
            "8.000",
            "First count"
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.systemPhysicalQuantity")
                    .value(10.0)
            )
            .andExpect(
                jsonPath("$.systemReservedQuantity")
                    .value(3.0)
            )
            .andExpect(
                jsonPath("$.countedQuantity")
                    .value(8.0)
            )
            .andExpect(
                jsonPath("$.differenceQuantity")
                    .value(-2.0)
            );

        jdbcTemplate.update(
            """
            UPDATE stock_balances
            SET physical_quantity = 12.000
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            stockItemId,
            stockLocationId
        );

        countItem(
            inventorySessionId,
            stockItemId,
            "9.000",
            "Second count"
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.systemPhysicalQuantity")
                    .value(10.0)
            )
            .andExpect(
                jsonPath("$.systemReservedQuantity")
                    .value(3.0)
            )
            .andExpect(
                jsonPath("$.countedQuantity")
                    .value(9.0)
            )
            .andExpect(
                jsonPath("$.differenceQuantity")
                    .value(-1.0)
            );

        assertThat(
            countLineCount(
                inventorySessionId,
                stockItemId
            )
        ).isEqualTo(1L);

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/sessions/{sessionId}",
                    inventorySessionId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("COUNTING")
            )
            .andExpect(
                jsonPath("$.lines.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.lines[0].systemPhysicalQuantity")
                    .value(10.0)
            );
    }

    @Test
    void countRejectsForeignStockItem()
        throws Exception {

        UUID stockLocationId =
            insertOwnedStockLocation(
                "FOREIGN-ITEM",
                true
            );

        UUID inventorySessionId =
            UUID.randomUUID();

        createSession(
            inventorySessionId,
            stockLocationId
        );

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-ITEM"
            );

        UUID foreignStockItem =
            insertStockItemForOrganization(
                foreignOrganization,
                "FOREIGN-ITEM"
            );

        countItem(
            inventorySessionId,
            foreignStockItem,
            "1.000",
            null
        )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            );

        assertThat(
            countLineCount(
                inventorySessionId,
                foreignStockItem
            )
        ).isZero();
    }

    @Test
    void completedSessionRejectsFurtherCounts()
        throws Exception {

        UUID stockItemId =
            insertOwnedStockItem(
                "CLOSED-COUNT"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "CLOSED-COUNT",
                true
            );

        UUID inventorySessionId =
            UUID.randomUUID();

        createSession(
            inventorySessionId,
            stockLocationId
        );

        countItem(
            inventorySessionId,
            stockItemId,
            "2.000",
            null
        );

        completeSession(
            inventorySessionId
        );

        countItem(
            inventorySessionId,
            stockItemId,
            "3.000",
            null
        )
            .andExpect(
                status().isConflict()
            );

        BigDecimal counted =
            jdbcTemplate.queryForObject(
                """
                SELECT counted_quantity
                FROM inventory_count_lines
                WHERE inventory_session_id = ?
                  AND stock_item_id = ?
                """,
                BigDecimal.class,
                inventorySessionId,
                stockItemId
            );

        assertThat(counted)
            .isEqualByComparingTo("2.000");
    }

    // =========================================================
    // COMPLETION
    // =========================================================

    @Test
    void sessionCannotCompleteWithoutCountedItems()
        throws Exception {

        UUID stockLocationId =
            insertOwnedStockLocation(
                "EMPTY",
                true
            );

        UUID inventorySessionId =
            UUID.randomUUID();

        createSession(
            inventorySessionId,
            stockLocationId
        );

        completeSession(
            inventorySessionId
        )
            .andExpect(
                status().isConflict()
            );

        assertThat(
            sessionStatus(
                inventorySessionId
            )
        ).isEqualTo("OPEN");
    }

    @Test
    void completeSessionIsIdempotent()
        throws Exception {

        UUID stockItemId =
            insertOwnedStockItem(
                "COMPLETE"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "COMPLETE",
                true
            );

        UUID inventorySessionId =
            UUID.randomUUID();

        createSession(
            inventorySessionId,
            stockLocationId
        );

        countItem(
            inventorySessionId,
            stockItemId,
            "1.000",
            null
        );

        completeSession(
            inventorySessionId
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.session.status")
                    .value("COMPLETED")
            );

        completeSession(
            inventorySessionId
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.session.status")
                    .value("COMPLETED")
            );
    }

    // =========================================================
    // APPLY
    // =========================================================

    @Test
    void applyCreatesAdjustmentMovementAndUpdatesBalance()
        throws Exception {

        UUID stockItemId =
            insertOwnedStockItem(
                "APPLY"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "APPLY",
                true
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "10.000",
            "2.000"
        );

        UUID inventorySessionId =
            UUID.randomUUID();

        createSession(
            inventorySessionId,
            stockLocationId
        );

        countItem(
            inventorySessionId,
            stockItemId,
            "7.000",
            "Physical correction"
        );

        completeSession(
            inventorySessionId
        );

        applySession(
            inventorySessionId
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.session.status")
                    .value("APPLIED")
            );

        assertBalance(
            stockItemId,
            stockLocationId,
            "7.000",
            "2.000"
        );

        Long movements =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM inventory_movements
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                  AND movement_type = 'ADJUSTMENT'
                  AND physical_delta = -3.000
                  AND reserved_delta = 0
                  AND reference_type = 'INVENTORY_SESSION'
                  AND reference_id = ?
                """,
                Long.class,
                stockItemId,
                stockLocationId,
                inventorySessionId
            );

        assertThat(movements)
            .isEqualTo(1L);

        UUID adjustmentId =
            jdbcTemplate.queryForObject(
                """
                SELECT adjustment_movement_id
                FROM inventory_count_lines
                WHERE inventory_session_id = ?
                  AND stock_item_id = ?
                """,
                UUID.class,
                inventorySessionId,
                stockItemId
            );

        assertThat(adjustmentId)
            .isNotNull();
    }

    @Test
    void zeroDifferenceCreatesNoMovementAndApplyReplayIsIdempotent()
        throws Exception {

        UUID stockItemId =
            insertOwnedStockItem(
                "ZERO"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "ZERO",
                true
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "5.000",
            "1.000"
        );

        UUID inventorySessionId =
            UUID.randomUUID();

        createSession(
            inventorySessionId,
            stockLocationId
        );

        countItem(
            inventorySessionId,
            stockItemId,
            "5.000",
            null
        );

        completeSession(
            inventorySessionId
        );

        applySession(
            inventorySessionId
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        assertThat(
            inventoryMovementCount(
                inventorySessionId
            )
        ).isZero();

        applySession(
            inventorySessionId
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        assertBalance(
            stockItemId,
            stockLocationId,
            "5.000",
            "1.000"
        );
    }

    @Test
    void staleSnapshotRollsBackWholeApplication()
        throws Exception {

        UUID itemOne =
            insertOwnedStockItem(
                "STALE-A"
            );

        UUID itemTwo =
            insertOwnedStockItem(
                "STALE-B"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "STALE",
                true
            );

        insertBalance(
            itemOne,
            stockLocationId,
            "10.000",
            "0.000"
        );

        insertBalance(
            itemTwo,
            stockLocationId,
            "20.000",
            "0.000"
        );

        UUID inventorySessionId =
            UUID.randomUUID();

        createSession(
            inventorySessionId,
            stockLocationId
        );

        countItem(
            inventorySessionId,
            itemOne,
            "8.000",
            null
        );

        countItem(
            inventorySessionId,
            itemTwo,
            "18.000",
            null
        );

        completeSession(
            inventorySessionId
        );

        jdbcTemplate.update(
            """
            UPDATE stock_balances
            SET physical_quantity = 21.000
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            itemTwo,
            stockLocationId
        );

        applySession(
            inventorySessionId
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );

        assertBalance(
            itemOne,
            stockLocationId,
            "10.000",
            "0.000"
        );

        assertBalance(
            itemTwo,
            stockLocationId,
            "21.000",
            "0.000"
        );

        assertThat(
            inventoryMovementCount(
                inventorySessionId
            )
        ).isZero();

        assertThat(
            adjustmentLinkCount(
                inventorySessionId
            )
        ).isZero();

        assertThat(
            sessionStatus(
                inventorySessionId
            )
        ).isEqualTo("COMPLETED");
    }

    @Test
    void countedPhysicalCannotBecomeLowerThanReserved()
        throws Exception {

        UUID stockItemId =
            insertOwnedStockItem(
                "RESERVED"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "RESERVED",
                true
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "10.000",
            "8.000"
        );

        UUID inventorySessionId =
            UUID.randomUUID();

        createSession(
            inventorySessionId,
            stockLocationId
        );

        countItem(
            inventorySessionId,
            stockItemId,
            "7.000",
            null
        );

        completeSession(
            inventorySessionId
        );

        applySession(
            inventorySessionId
        )
            .andExpect(
                status().isConflict()
            );

        assertBalance(
            stockItemId,
            stockLocationId,
            "10.000",
            "8.000"
        );

        assertThat(
            inventoryMovementCount(
                inventorySessionId
            )
        ).isZero();

        assertThat(
            sessionStatus(
                inventorySessionId
            )
        ).isEqualTo("COMPLETED");
    }

    // =========================================================
    // CANCEL / TENANT
    // =========================================================

    @Test
    void cancelIsIdempotentButAppliedSessionCannotBeCancelled()
        throws Exception {

        UUID stockLocationId =
            insertOwnedStockLocation(
                "CANCEL",
                true
            );

        UUID cancelledSession =
            UUID.randomUUID();

        createSession(
            cancelledSession,
            stockLocationId
        );

        cancelSession(
            cancelledSession
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.session.status")
                    .value("CANCELLED")
            );

        cancelSession(
            cancelledSession
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        UUID stockItemId =
            insertOwnedStockItem(
                "APPLIED-CANCEL"
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "2.000",
            "0.000"
        );

        UUID appliedSession =
            UUID.randomUUID();

        createSession(
            appliedSession,
            stockLocationId
        );

        countItem(
            appliedSession,
            stockItemId,
            "1.000",
            null
        );

        completeSession(
            appliedSession
        );

        applySession(
            appliedSession
        );

        cancelSession(
            appliedSession
        )
            .andExpect(
                status().isConflict()
            );

        assertThat(
            sessionStatus(
                appliedSession
            )
        ).isEqualTo("APPLIED");
    }

    @Test
    void foreignSessionIsHidden()
        throws Exception {

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-SESSION"
            );

        UUID foreignUser =
            insertUser(
                foreignOrganization
            );

        UUID foreignLocation =
            insertStockLocationForOrganization(
                foreignOrganization,
                "FOREIGN-SESSION",
                true
            );

        UUID inventorySessionId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO inventory_sessions (
                id,
                stock_location_id,
                status,
                started_by,
                started_at
            )
            VALUES (
                ?,
                ?,
                'OPEN',
                ?,
                CURRENT_TIMESTAMP
            )
            """,
            inventorySessionId,
            foreignLocation,
            foreignUser
        );

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/sessions/{sessionId}",
                    inventorySessionId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            );
    }

    // =========================================================
    // MOCKMVC HELPERS
    // =========================================================

    private void createSession(
        UUID inventorySessionId,
        UUID stockLocationId
    ) throws Exception {

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/sessions/{sessionId}",
                    inventorySessionId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        sessionBody(
                            stockLocationId,
                            null
                        )
                    )
            )
            .andExpect(
                status().isOk()
            );
    }

    private org.springframework.test.web.servlet.ResultActions
        countItem(
            UUID inventorySessionId,
            UUID stockItemId,
            String quantity,
            String reason
        ) throws Exception {

        String reasonJson =
            reason == null
                ? ""
                : """
                    ,
                    "reason": "%s"
                    """.formatted(reason);

        String body =
            """
            {
              "countedQuantity": %s
              %s
            }
            """.formatted(
                quantity,
                reasonJson
            );

        return mockMvc.perform(
            put(
                "/api/v1/admin/inventory/sessions/{sessionId}/items/{stockItemId}",
                inventorySessionId,
                stockItemId
            )
                .header(
                    "Authorization",
                    bearer("product.write")
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(body)
        );
    }

    private org.springframework.test.web.servlet.ResultActions
        completeSession(
            UUID inventorySessionId
        ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/admin/inventory/sessions/{sessionId}/complete",
                inventorySessionId
            )
                .header(
                    "Authorization",
                    bearer("product.write")
                )
        );
    }

    private org.springframework.test.web.servlet.ResultActions
        applySession(
            UUID inventorySessionId
        ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/admin/inventory/sessions/{sessionId}/apply",
                inventorySessionId
            )
                .header(
                    "Authorization",
                    bearer("product.write")
                )
        );
    }

    private org.springframework.test.web.servlet.ResultActions
        cancelSession(
            UUID inventorySessionId
        ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/admin/inventory/sessions/{sessionId}/cancel",
                inventorySessionId
            )
                .header(
                    "Authorization",
                    bearer("product.write")
                )
        );
    }

    private String sessionBody(
        UUID stockLocationId,
        String notes
    ) {

        if (notes == null) {
            return """
                {
                  "stockLocationId": "%s"
                }
                """.formatted(
                    stockLocationId
                );
        }

        return """
            {
              "stockLocationId": "%s",
              "notes": "%s"
            }
            """.formatted(
                stockLocationId,
                notes
            );
    }

    // =========================================================
    // JWT
    // =========================================================

    private String bearer(
        String... permissions
    ) {

        return "Bearer "
            + token(permissions);
    }

    private String token(
        String... permissions
    ) {

        Instant now =
            Instant.now();

        JwtClaimsSet claims =
            JwtClaimsSet.builder()
                .issuer(
                    securityProperties
                        .jwt()
                        .issuer()
                )
                .audience(
                    List.of(
                        securityProperties
                            .audience()
                    )
                )
                .subject(
                    userId.toString()
                )
                .issuedAt(now)
                .expiresAt(
                    now.plusSeconds(600)
                )
                .id(
                    UUID.randomUUID()
                        .toString()
                )
                .claim(
                    "sid",
                    sessionId
                )
                .claim(
                    "email",
                    email
                )
                .claim(
                    "roles",
                    List.of()
                )
                .claim(
                    "permissions",
                    List.of(permissions)
                )
                .claim(
                    "role_scopes",
                    List.of()
                )
                .build();

        return jwtEncoder
            .encode(
                JwtEncoderParameters.from(
                    claims
                )
            )
            .getTokenValue();
    }

    // =========================================================
    // DATABASE FIXTURES
    // =========================================================

    private UUID insertOwnedStockItem(
        String prefix
    ) {

        return insertStockItemForOrganization(
            organizationId,
            prefix
        );
    }

    private UUID insertStockItemForOrganization(
        UUID tenantId,
        String prefix
    ) {

        UUID categoryId =
            UUID.randomUUID();

        UUID productId =
            UUID.randomUUID();

        UUID stockItemId =
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
                ?, ?, ?, ?, ?,
                'PACKAGED',
                10.00,
                0.00,
                0,
                TRUE,
                FALSE,
                TRUE
            )
            """,
            productId,
            tenantId,
            categoryId,
            prefix + "-" + randomSuffix(),
            prefix + " Product"
        );

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
            stockItemId,
            tenantId,
            productId
        );

        return stockItemId;
    }

    private UUID insertOwnedStockLocation(
        String prefix,
        boolean active
    ) {

        return insertStockLocationForOrganization(
            organizationId,
            prefix,
            active
        );
    }

    private UUID insertStockLocationForOrganization(
        UUID tenantId,
        String prefix,
        boolean active
    ) {

        UUID campusId =
            UUID.randomUUID();

        UUID locationId =
            UUID.randomUUID();

        UUID stockLocationId =
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
            campusId,
            tenantId,
            prefix + " Campus",
            "C" + randomSuffix()
        );

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
            VALUES (?, ?, ?, ?, 'STORAGE', TRUE)
            """,
            locationId,
            campusId,
            prefix + " Location",
            "L" + randomSuffix()
        );

        jdbcTemplate.update(
            """
            INSERT INTO stock_locations (
                id,
                location_id,
                name,
                type,
                is_active
            )
            VALUES (?, ?, ?, 'STORAGE', ?)
            """,
            stockLocationId,
            locationId,
            prefix + " Stock",
            active
        );

        return stockLocationId;
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
            VALUES (?, ?, ?, TRUE)
            """,
            id,
            prefix + " Org",
            "ORG" + randomSuffix()
        );

        return id;
    }

    private UUID insertUser(
        UUID tenantId
    ) {

        UUID id =
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
            VALUES (?, ?, ?, 'Foreign', 'Counter', 'ACTIVE')
            """,
            id,
            tenantId,
            "foreign-count-"
                + randomSuffix()
                + "@sup2i.test"
        );

        return id;
    }

    private void insertBalance(
        UUID stockItemId,
        UUID stockLocationId,
        String physical,
        String reserved
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO stock_balances (
                stock_item_id,
                stock_location_id,
                physical_quantity,
                reserved_quantity
            )
            VALUES (?, ?, ?, ?)
            """,
            stockItemId,
            stockLocationId,
            new BigDecimal(physical),
            new BigDecimal(reserved)
        );
    }

    // =========================================================
    // DATABASE ASSERTIONS
    // =========================================================

    private void assertBalance(
        UUID stockItemId,
        UUID stockLocationId,
        String physical,
        String reserved
    ) {

        BigDecimal actualPhysical =
            jdbcTemplate.queryForObject(
                """
                SELECT physical_quantity
                FROM stock_balances
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                """,
                BigDecimal.class,
                stockItemId,
                stockLocationId
            );

        BigDecimal actualReserved =
            jdbcTemplate.queryForObject(
                """
                SELECT reserved_quantity
                FROM stock_balances
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                """,
                BigDecimal.class,
                stockItemId,
                stockLocationId
            );

        assertThat(actualPhysical)
            .isEqualByComparingTo(physical);

        assertThat(actualReserved)
            .isEqualByComparingTo(reserved);
    }

    private Long sessionCount(
        UUID inventorySessionId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_sessions
            WHERE id = ?
            """,
            Long.class,
            inventorySessionId
        );
    }

    private String sessionStatus(
        UUID inventorySessionId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM inventory_sessions
            WHERE id = ?
            """,
            String.class,
            inventorySessionId
        );
    }

    private Long countLineCount(
        UUID inventorySessionId,
        UUID stockItemId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_count_lines
            WHERE inventory_session_id = ?
              AND stock_item_id = ?
            """,
            Long.class,
            inventorySessionId,
            stockItemId
        );
    }

    private Long inventoryMovementCount(
        UUID inventorySessionId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_movements
            WHERE reference_type = 'INVENTORY_SESSION'
              AND reference_id = ?
            """,
            Long.class,
            inventorySessionId
        );
    }

    private Long adjustmentLinkCount(
        UUID inventorySessionId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_count_lines
            WHERE inventory_session_id = ?
              AND adjustment_movement_id IS NOT NULL
            """,
            Long.class,
            inventorySessionId
        );
    }

    private String randomSuffix() {

        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10);
    }
}