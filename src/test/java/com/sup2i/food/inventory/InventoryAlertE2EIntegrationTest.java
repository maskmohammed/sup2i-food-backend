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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
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
class InventoryAlertE2EIntegrationTest {

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
    private String authSessionId;

    @BeforeEach
    void seedTenantAndSession() {

        String suffix =
            randomSuffix();

        organizationId =
            UUID.randomUUID();

        userId =
            UUID.randomUUID();

        email =
            "inventory-alert-"
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
            "Inventory Alert " + suffix,
            "IA" + suffix
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
            "Alert"
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "inventory-alert-e2e",
                InetAddress.getLoopbackAddress()
            );

        Jwt jwt =
            jwtDecoder.decode(
                tokens.accessToken()
            );

        authSessionId =
            jwt.getClaimAsString("sid");
    }

    @Test
    void alertsRequireProductWritePermission()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/alerts/reconcile"
                )
                    .header(
                        "Authorization",
                        bearer("catalog.read")
                    )
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
    void lowStockUsesAvailableQuantityAndLowSeverity()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "LOW",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "LOW",
                "20.000",
                false
            );

        insertBalance(
            item,
            location,
            "20.000",
            "8.000"
        );

        reconcile()
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.created")
                    .value(1)
            );

        assertAlert(
            item,
            location,
            "LOW_STOCK",
            "OPEN",
            "LOW",
            "20.000",
            "12.000"
        );
    }

    @Test
    void lowStockBecomesCriticalAtTwentyFivePercentBoundary()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "CRITICAL",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "CRITICAL",
                "20.000",
                false
            );

        insertBalance(
            item,
            location,
            "5.000",
            "0.000"
        );

        reconcile()
            .andExpect(status().isOk());

        assertAlert(
            item,
            location,
            "LOW_STOCK",
            "OPEN",
            "CRITICAL",
            "20.000",
            "5.000"
        );
    }

    @Test
    void acknowledgedAlertKeepsAcknowledgementWhileSeverityChanges()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "ACK",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "ACK",
                "20.000",
                false
            );

        insertBalance(
            item,
            location,
            "15.000",
            "0.000"
        );

        reconcile();

        UUID alertId =
            activeAlertId(
                item,
                location,
                "LOW_STOCK"
            );

        acknowledge(alertId)
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.alert.status")
                    .value("ACKNOWLEDGED")
            );

        acknowledge(alertId)
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        jdbcTemplate.update(
            """
            UPDATE stock_balances
            SET physical_quantity = 4.000
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            item,
            location
        );

        reconcile()
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.retained")
                    .value(1)
            );

        assertThat(
            alertStatus(alertId)
        ).isEqualTo("ACKNOWLEDGED");

        assertThat(
            alertSeverity(alertId)
        ).isEqualTo("CRITICAL");

        assertThat(
            alertObserved(alertId)
        ).isEqualByComparingTo("4.000");

        assertThat(
            acknowledgedBy(alertId)
        ).isEqualTo(userId);
    }

    @Test
    void outOfStockSupersedesLowStock()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "OUT",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "OUT",
                "20.000",
                false
            );

        insertBalance(
            item,
            location,
            "10.000",
            "0.000"
        );

        reconcile();

        UUID lowAlert =
            activeAlertId(
                item,
                location,
                "LOW_STOCK"
            );

        jdbcTemplate.update(
            """
            UPDATE stock_balances
            SET physical_quantity = 3.000,
                reserved_quantity = 3.000
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            item,
            location
        );

        reconcile()
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.created")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.resolved")
                    .value(1)
            );

        assertThat(
            alertStatus(lowAlert)
        ).isEqualTo("RESOLVED");

        assertAlert(
            item,
            location,
            "OUT_OF_STOCK",
            "OPEN",
            "OUT_OF_STOCK",
            "20.000",
            "0.000"
        );

        assertThat(
            activeAlertCount(
                item,
                location
            )
        ).isEqualTo(1L);
    }

    @Test
    void outOfStockExistsEvenWithoutLowStockThreshold()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "NULL-THRESHOLD",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "NULL-THRESHOLD",
                null,
                false
            );

        insertBalance(
            item,
            location,
            "0.000",
            "0.000"
        );

        reconcile();

        assertAlert(
            item,
            location,
            "OUT_OF_STOCK",
            "OPEN",
            "OUT_OF_STOCK",
            null,
            "0.000"
        );
    }

    @Test
    void recoveryResolvesManagedStockAlert()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "RECOVERY",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "RECOVERY",
                "10.000",
                false
            );

        insertBalance(
            item,
            location,
            "2.000",
            "0.000"
        );

        reconcile();

        UUID alertId =
            activeAlertId(
                item,
                location,
                "LOW_STOCK"
            );

        jdbcTemplate.update(
            """
            UPDATE stock_balances
            SET physical_quantity = 11.000
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            item,
            location
        );

        reconcile()
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.resolved")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.active")
                    .value(0)
            );

        assertThat(
            alertStatus(alertId)
        ).isEqualTo("RESOLVED");

        assertThat(
            resolvedAt(alertId)
        ).isNotNull();
    }

    @Test
    void recurringConditionCreatesNewAlertAfterResolution()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "RETURN",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "RETURN",
                "10.000",
                false
            );

        insertBalance(
            item,
            location,
            "5.000",
            "0.000"
        );

        reconcile();

        UUID first =
            activeAlertId(
                item,
                location,
                "LOW_STOCK"
            );

        jdbcTemplate.update(
            """
            UPDATE stock_balances
            SET physical_quantity = 20.000
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            item,
            location
        );

        reconcile();

        jdbcTemplate.update(
            """
            UPDATE stock_balances
            SET physical_quantity = 5.000
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            item,
            location
        );

        reconcile();

        UUID second =
            activeAlertId(
                item,
                location,
                "LOW_STOCK"
            );

        assertThat(second)
            .isNotEqualTo(first);

        assertThat(
            alertStatus(first)
        ).isEqualTo("RESOLVED");

        assertThat(
            alertStatus(second)
        ).isEqualTo("OPEN");
    }

    @Test
    void resolvedAlertCannotBeAcknowledged()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "RESOLVED-ACK",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "RESOLVED-ACK",
                "10.000",
                false
            );

        insertBalance(
            item,
            location,
            "1.000",
            "0.000"
        );

        reconcile();

        UUID alertId =
            activeAlertId(
                item,
                location,
                "LOW_STOCK"
            );

        jdbcTemplate.update(
            """
            UPDATE stock_balances
            SET physical_quantity = 20.000
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            item,
            location
        );

        reconcile();

        acknowledge(alertId)
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );
    }

    @Test
    void expiryAlertsUseCriticalLowAndInfoBands()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "EXPIRY",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "EXPIRY",
                null,
                true
            );

        UUID expired =
            insertLot(
                item,
                location,
                "EXPIRED",
                OffsetDateTime.now()
                    .minusHours(2)
            );

        UUID within24Hours =
            insertLot(
                item,
                location,
                "24H",
                OffsetDateTime.now()
                    .plusHours(12)
            );

        UUID withinThreeDays =
            insertLot(
                item,
                location,
                "3D",
                OffsetDateTime.now()
                    .plusDays(2)
            );

        UUID withinSevenDays =
            insertLot(
                item,
                location,
                "7D",
                OffsetDateTime.now()
                    .plusDays(5)
            );

        UUID outsideWindow =
            insertLot(
                item,
                location,
                "OUTSIDE",
                OffsetDateTime.now()
                    .plusDays(10)
            );

        reconcile()
            .andExpect(status().isOk());

        assertExpirySeverity(
            expired,
            "CRITICAL"
        );

        assertExpirySeverity(
            within24Hours,
            "CRITICAL"
        );

        assertExpirySeverity(
            withinThreeDays,
            "LOW"
        );

        assertExpirySeverity(
            withinSevenDays,
            "INFO"
        );

        assertThat(
            activeExpiryAlertCount(
                outsideWindow
            )
        ).isZero();
    }

    @Test
    void consumedLotAutomaticallyResolvesExpiryAlert()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "CONSUMED",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "CONSUMED",
                null,
                true
            );

        UUID lot =
            insertLot(
                item,
                location,
                "CONSUMED",
                OffsetDateTime.now()
                    .plusDays(2)
            );

        reconcile();

        UUID alertId =
            activeExpiryAlertId(lot);

        jdbcTemplate.update(
            """
            UPDATE stock_lots
            SET quantity_remaining = 0
            WHERE id = ?
            """,
            lot
        );

        reconcile()
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.resolved")
                    .value(1)
            );

        assertThat(
            alertStatus(alertId)
        ).isEqualTo("RESOLVED");
    }

    @Test
    void expiryIgnoredWhenItemDoesNotTrackExpiry()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "NO-EXPIRY",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "NO-EXPIRY",
                null,
                false
            );

        UUID lot =
            insertLot(
                item,
                location,
                "NO-EXPIRY",
                OffsetDateTime.now()
                    .plusHours(3)
            );

        reconcile();

        assertThat(
            activeExpiryAlertCount(lot)
        ).isZero();
    }

    @Test
    void reconciliationDeduplicatesExistingManagedAlerts()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "DEDUPE",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "DEDUPE",
                "10.000",
                false
            );

        insertBalance(
            item,
            location,
            "5.000",
            "0.000"
        );

        UUID first =
            insertRawAlert(
                item,
                location,
                null,
                "LOW_STOCK",
                "OPEN",
                "LOW"
            );

        UUID second =
            insertRawAlert(
                item,
                location,
                null,
                "LOW_STOCK",
                "OPEN",
                "LOW"
            );

        reconcile()
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.retained")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.resolved")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.created")
                    .value(0)
            );

        assertThat(
            activeAlertCount(
                item,
                location
            )
        ).isEqualTo(1L);

        List<String> statuses =
            jdbcTemplate.queryForList(
                """
                SELECT status
                FROM stock_alerts
                WHERE id IN (?, ?)
                ORDER BY id
                """,
                String.class,
                first,
                second
            );

        assertThat(statuses)
            .containsExactlyInAnyOrder(
                "OPEN",
                "RESOLVED"
            );
    }

    @Test
    void unmanagedAlertTypesAreNeverResolvedByInventoryE()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "UNMANAGED",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "UNMANAGED",
                null,
                false
            );

        UUID reorder =
            insertRawAlert(
                item,
                location,
                null,
                "REORDER",
                "OPEN",
                "LOW"
            );

        UUID unusual =
            insertRawAlert(
                item,
                location,
                null,
                "UNUSUAL_CONSUMPTION",
                "ACKNOWLEDGED",
                "INFO"
            );

        reconcile()
            .andExpect(status().isOk());

        assertThat(
            alertStatus(reorder)
        ).isEqualTo("OPEN");

        assertThat(
            alertStatus(unusual)
        ).isEqualTo("ACKNOWLEDGED");
    }

    @Test
    void alertSearchAndTenantIsolationAreEnforced()
        throws Exception {

        UUID locationOne =
            insertOwnedStockLocation(
                "FILTER-A",
                true
            );

        UUID locationTwo =
            insertOwnedStockLocation(
                "FILTER-B",
                true
            );

        UUID itemOne =
            insertOwnedStockItem(
                "FILTER-A",
                "10.000",
                false
            );

        UUID itemTwo =
            insertOwnedStockItem(
                "FILTER-B",
                "10.000",
                false
            );

        insertBalance(
            itemOne,
            locationOne,
            "5.000",
            "0.000"
        );

        insertBalance(
            itemTwo,
            locationTwo,
            "0.000",
            "0.000"
        );

        reconcile();

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/alerts"
                )
                    .param(
                        "alertType",
                        "LOW_STOCK"
                    )
                    .param(
                        "stockLocationId",
                        locationOne.toString()
                    )
                    .param(
                        "stockItemId",
                        itemOne.toString()
                    )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$[0].alertType")
                    .value("LOW_STOCK")
            );

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-ALERT"
            );

        UUID foreignLocation =
            insertStockLocationForOrganization(
                foreignOrganization,
                "FOREIGN",
                true
            );

        UUID foreignItem =
            insertStockItemForOrganization(
                foreignOrganization,
                "FOREIGN",
                "10.000",
                false
            );

        UUID foreignAlert =
            insertRawAlert(
                foreignItem,
                foreignLocation,
                null,
                "LOW_STOCK",
                "OPEN",
                "LOW"
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/alerts/{alertId}",
                    foreignAlert
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
            )
            .andExpect(
                status().isNotFound()
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/alerts"
                )
                    .param(
                        "stockItemId",
                        foreignItem.toString()
                    )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
            )
            .andExpect(
                status().isNotFound()
            );
    }

    @Test
    void inactiveLocationResolvesManagedAlerts()
        throws Exception {

        UUID location =
            insertOwnedStockLocation(
                "INACTIVE",
                true
            );

        UUID item =
            insertOwnedStockItem(
                "INACTIVE",
                "10.000",
                false
            );

        insertBalance(
            item,
            location,
            "5.000",
            "0.000"
        );

        reconcile();

        UUID alertId =
            activeAlertId(
                item,
                location,
                "LOW_STOCK"
            );

        jdbcTemplate.update(
            """
            UPDATE stock_locations
            SET is_active = FALSE
            WHERE id = ?
            """,
            location
        );

        reconcile();

        assertThat(
            alertStatus(alertId)
        ).isEqualTo("RESOLVED");
    }

    private ResultActions reconcile()
        throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/admin/inventory/alerts/reconcile"
            )
                .header(
                    "Authorization",
                    bearer("product.write")
                )
        );
    }

    private ResultActions acknowledge(
        UUID alertId
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/admin/inventory/alerts/{alertId}/acknowledge",
                alertId
            )
                .header(
                    "Authorization",
                    bearer("product.write")
                )
        );
    }

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
                    authSessionId
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

    private UUID insertOwnedStockItem(
        String prefix,
        String threshold,
        boolean trackExpiry
    ) {

        return insertStockItemForOrganization(
            organizationId,
            prefix,
            threshold,
            trackExpiry
        );
    }

    private UUID insertStockItemForOrganization(
        UUID tenantId,
        String prefix,
        String threshold,
        boolean trackExpiry
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
                low_stock_threshold,
                track_expiry
            )
            VALUES (?, ?, ?, 'PIECE', ?, ?)
            """,
            stockItemId,
            tenantId,
            productId,
            threshold == null
                ? null
                : new BigDecimal(threshold),
            trackExpiry
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

    private void insertBalance(
        UUID stockItem,
        UUID stockLocation,
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
            stockItem,
            stockLocation,
            new BigDecimal(physical),
            new BigDecimal(reserved)
        );
    }

    private UUID insertLot(
        UUID stockItem,
        UUID stockLocation,
        String lotNumber,
        OffsetDateTime expiresAt
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_lots (
                id,
                stock_item_id,
                stock_location_id,
                lot_number,
                received_at,
                expires_at,
                quantity_received,
                quantity_remaining
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                CURRENT_TIMESTAMP,
                ?,
                10.000,
                10.000
            )
            """,
            id,
            stockItem,
            stockLocation,
            lotNumber,
            expiresAt
        );

        return id;
    }

    private UUID insertRawAlert(
        UUID stockItem,
        UUID stockLocation,
        UUID lot,
        String type,
        String status,
        String severity
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_alerts (
                id,
                stock_item_id,
                stock_location_id,
                alert_type,
                status,
                threshold_value,
                observed_value,
                lot_id,
                detected_at,
                acknowledged_by,
                acknowledged_at,
                severity
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                10.000,
                5.000,
                ?,
                CURRENT_TIMESTAMP,
                CASE
                    WHEN ? = 'ACKNOWLEDGED'
                    THEN ?
                    ELSE NULL
                END,
                CASE
                    WHEN ? = 'ACKNOWLEDGED'
                    THEN CURRENT_TIMESTAMP
                    ELSE NULL
                END,
                ?
            )
            """,
            id,
            stockItem,
            stockLocation,
            type,
            status,
            lot,
            status,
            userId,
            status,
            severity
        );

        return id;
    }

    private void assertAlert(
        UUID item,
        UUID location,
        String type,
        String status,
        String severity,
        String threshold,
        String observed
    ) {

        String actualStatus =
            jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM stock_alerts
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                  AND alert_type = ?
                  AND status <> 'RESOLVED'
                """,
                String.class,
                item,
                location,
                type
            );

        String actualSeverity =
            jdbcTemplate.queryForObject(
                """
                SELECT severity
                FROM stock_alerts
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                  AND alert_type = ?
                  AND status <> 'RESOLVED'
                """,
                String.class,
                item,
                location,
                type
            );

        BigDecimal actualThreshold =
            jdbcTemplate.queryForObject(
                """
                SELECT threshold_value
                FROM stock_alerts
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                  AND alert_type = ?
                  AND status <> 'RESOLVED'
                """,
                BigDecimal.class,
                item,
                location,
                type
            );

        BigDecimal actualObserved =
            jdbcTemplate.queryForObject(
                """
                SELECT observed_value
                FROM stock_alerts
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                  AND alert_type = ?
                  AND status <> 'RESOLVED'
                """,
                BigDecimal.class,
                item,
                location,
                type
            );

        assertThat(actualStatus)
            .isEqualTo(status);

        assertThat(actualSeverity)
            .isEqualTo(severity);

        if (threshold == null) {
            assertThat(actualThreshold)
                .isNull();
        } else {
            assertThat(actualThreshold)
                .isEqualByComparingTo(
                    threshold
                );
        }

        assertThat(actualObserved)
            .isEqualByComparingTo(
                observed
            );
    }

    private void assertExpirySeverity(
        UUID lot,
        String severity
    ) {

        String actual =
            jdbcTemplate.queryForObject(
                """
                SELECT severity
                FROM stock_alerts
                WHERE lot_id = ?
                  AND alert_type = 'EXPIRY'
                  AND status <> 'RESOLVED'
                """,
                String.class,
                lot
            );

        assertThat(actual)
            .isEqualTo(severity);
    }

    private UUID activeAlertId(
        UUID item,
        UUID location,
        String type
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM stock_alerts
            WHERE stock_item_id = ?
              AND stock_location_id = ?
              AND alert_type = ?
              AND status IN ('OPEN','ACKNOWLEDGED')
            """,
            UUID.class,
            item,
            location,
            type
        );
    }

    private UUID activeExpiryAlertId(
        UUID lot
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM stock_alerts
            WHERE lot_id = ?
              AND alert_type = 'EXPIRY'
              AND status IN ('OPEN','ACKNOWLEDGED')
            """,
            UUID.class,
            lot
        );
    }

    private Long activeExpiryAlertCount(
        UUID lot
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM stock_alerts
            WHERE lot_id = ?
              AND alert_type = 'EXPIRY'
              AND status IN ('OPEN','ACKNOWLEDGED')
            """,
            Long.class,
            lot
        );
    }

    private Long activeAlertCount(
        UUID item,
        UUID location
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM stock_alerts
            WHERE stock_item_id = ?
              AND stock_location_id = ?
              AND status IN ('OPEN','ACKNOWLEDGED')
              AND alert_type IN (
                    'LOW_STOCK',
                    'OUT_OF_STOCK'
              )
            """,
            Long.class,
            item,
            location
        );
    }

    private String alertStatus(
        UUID alert
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM stock_alerts
            WHERE id = ?
            """,
            String.class,
            alert
        );
    }

    private String alertSeverity(
        UUID alert
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT severity
            FROM stock_alerts
            WHERE id = ?
            """,
            String.class,
            alert
        );
    }

    private BigDecimal alertObserved(
        UUID alert
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT observed_value
            FROM stock_alerts
            WHERE id = ?
            """,
            BigDecimal.class,
            alert
        );
    }

    private UUID acknowledgedBy(
        UUID alert
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT acknowledged_by
            FROM stock_alerts
            WHERE id = ?
            """,
            UUID.class,
            alert
        );
    }

    private OffsetDateTime resolvedAt(
        UUID alert
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT resolved_at
            FROM stock_alerts
            WHERE id = ?
            """,
            OffsetDateTime.class,
            alert
        );
    }

    private String randomSuffix() {

        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10);
    }
}