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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
class InventoryPublicContractE2EIntegrationTest {

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

        String random =
            randomSuffix();

        organizationId =
            UUID.randomUUID();

        userId =
            UUID.randomUUID();

        email =
            "inventory-public-"
                + random
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
            "Inventory Public " + random,
            "IP" + random
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
            "Public"
        );

        User user =
            userRepository
                .findById(
                    userId
                )
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "inventory-public-e2e",
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

        assertThat(sessionId)
            .isNotBlank();
    }

    @Test
    void publicReadRequiresInventoryReadPermission()
        throws Exception {

        UUID stockLocationId =
            insertOwnedStockLocation(
                "READ-RBAC"
            );

        mockMvc.perform(
                get(
                    "/api/v1/inventory/items"
                )
                    .param(
                        "stockLocationId",
                        stockLocationId.toString()
                    )
                    .header(
                        "Authorization",
                        bearer(
                            "product.write"
                        )
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
    void publicAdjustmentRequiresInventoryAdjustPermission()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "ADJUST-RBAC"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE",
                "5.000"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "ADJUST-RBAC"
            );

        mockMvc.perform(
                post(
                    "/api/v1/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer(
                            "product.write"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        "adjust-rbac-0001"
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        adjustmentBody(
                            stockItemId,
                            stockLocationId,
                            "1.000",
                            "INVENTORY",
                            "RBAC"
                        )
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
    void publicItemsAreLocationScopedAndLowStockFilterWorks()
        throws Exception {

        UUID stockLocationId =
            insertOwnedStockLocation(
                "LIST"
            );

        UUID otherStockLocationId =
            insertOwnedStockLocation(
                "LIST-OTHER"
            );

        UUID lowProductId =
            insertOwnedProduct(
                "LOW"
            );

        UUID highProductId =
            insertOwnedProduct(
                "HIGH"
            );

        UUID otherProductId =
            insertOwnedProduct(
                "OTHER"
            );

        UUID lowStockItemId =
            insertStockItemForProduct(
                organizationId,
                lowProductId,
                "PIECE",
                "5.000"
            );

        UUID highStockItemId =
            insertStockItemForProduct(
                organizationId,
                highProductId,
                "PIECE",
                "5.000"
            );

        UUID otherStockItemId =
            insertStockItemForProduct(
                organizationId,
                otherProductId,
                "PIECE",
                "5.000"
            );

        insertBalance(
            lowStockItemId,
            stockLocationId,
            "4.000",
            "0.000"
        );

        insertBalance(
            highStockItemId,
            stockLocationId,
            "8.000",
            "0.000"
        );

        insertBalance(
            otherStockItemId,
            otherStockLocationId,
            "1.000",
            "0.000"
        );

        mockMvc.perform(
                get(
                    "/api/v1/inventory/items"
                )
                    .param(
                        "stockLocationId",
                        stockLocationId.toString()
                    )
                    .header(
                        "Authorization",
                        bearer(
                            "inventory.read"
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(2)
            );

        mockMvc.perform(
                get(
                    "/api/v1/inventory/items"
                )
                    .param(
                        "stockLocationId",
                        stockLocationId.toString()
                    )
                    .param(
                        "lowStockOnly",
                        "true"
                    )
                    .header(
                        "Authorization",
                        bearer(
                            "inventory.read"
                        )
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
                jsonPath("$[0].stockItemId")
                    .value(
                        lowStockItemId.toString()
                    )
            )
            .andExpect(
                jsonPath("$[0].stockLocationId")
                    .value(
                        stockLocationId.toString()
                    )
            )
            .andExpect(
                jsonPath("$[0].itemType")
                    .value("PRODUCT")
            )
            .andExpect(
                jsonPath("$[0].availableQuantity")
                    .value(4.0)
            )
            .andExpect(
                jsonPath("$[0].lowStock")
                    .value(true)
            );
    }

    @Test
    void publicItemsCannotCrossTenantBoundary()
        throws Exception {

        UUID foreignOrganizationId =
            insertOrganization(
                "FOREIGN-LIST"
            );

        UUID foreignCampusId =
            insertCampus(
                foreignOrganizationId,
                "FOREIGN-LIST"
            );

        UUID foreignLocationId =
            insertLocation(
                foreignCampusId,
                "FOREIGN-LIST"
            );

        UUID foreignStockLocationId =
            insertStockLocation(
                foreignLocationId,
                "Foreign List"
            );

        mockMvc.perform(
                get(
                    "/api/v1/inventory/items"
                )
                    .param(
                        "stockLocationId",
                        foreignStockLocationId.toString()
                    )
                    .header(
                        "Authorization",
                        bearer(
                            "inventory.read"
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("RESOURCE_NOT_FOUND")
            );
    }

    @Test
    void publicAdjustmentCreatesMovementWith201()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "CREATE"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE",
                "5.000"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "CREATE"
            );

        mockMvc.perform(
                post(
                    "/api/v1/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer(
                            "inventory.adjust"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        "create-adjustment-0001"
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        adjustmentBody(
                            stockItemId,
                            stockLocationId,
                            "5.000",
                            "INVENTORY",
                            "Initial count"
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.movementType")
                    .value("ADJUSTMENT")
            )
            .andExpect(
                jsonPath("$.quantity")
                    .value(5.0)
            )
            .andExpect(
                jsonPath("$.reason")
                    .value("INVENTORY")
            );

        assertBalance(
            stockItemId,
            stockLocationId,
            "5.000",
            "0.000"
        );

        assertThat(
            movementCount(
                stockItemId,
                stockLocationId
            )
        ).isEqualTo(1L);
    }

    @Test
    void publicAdjustmentReplayIsStable()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "REPLAY"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE",
                "5.000"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "REPLAY"
            );

        String key =
            "replay-adjustment-0001";

        String body =
            adjustmentBody(
                stockItemId,
                stockLocationId,
                "5.000",
                "INVENTORY",
                "Replay"
            );

        mockMvc.perform(
                post(
                    "/api/v1/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer(
                            "inventory.adjust"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        key
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(body)
            )
            .andExpect(
                status().isCreated()
            );

        UUID movementId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM inventory_movements
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                """,
                UUID.class,
                stockItemId,
                stockLocationId
            );

        mockMvc.perform(
                post(
                    "/api/v1/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer(
                            "inventory.adjust"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        key
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(body)
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.id")
                    .value(
                        movementId.toString()
                    )
            );

        assertBalance(
            stockItemId,
            stockLocationId,
            "5.000",
            "0.000"
        );

        assertThat(
            movementCount(
                stockItemId,
                stockLocationId
            )
        ).isEqualTo(1L);
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadConflicts()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "IDEMPOTENCY-CONFLICT"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE",
                "5.000"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "IDEMPOTENCY-CONFLICT"
            );

        String key =
            "idempotency-conflict-0001";

        mockMvc.perform(
                post(
                    "/api/v1/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer(
                            "inventory.adjust"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        key
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        adjustmentBody(
                            stockItemId,
                            stockLocationId,
                            "5.000",
                            "INVENTORY",
                            "Original"
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            );

        mockMvc.perform(
                post(
                    "/api/v1/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer(
                            "inventory.adjust"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        key
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        adjustmentBody(
                            stockItemId,
                            stockLocationId,
                            "6.000",
                            "INVENTORY",
                            "Original"
                        )
                    )
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("IDEMPOTENCY_CONFLICT")
            );

        assertBalance(
            stockItemId,
            stockLocationId,
            "5.000",
            "0.000"
        );

        assertThat(
            movementCount(
                stockItemId,
                stockLocationId
            )
        ).isEqualTo(1L);
    }

    @Test
    void invalidZeroAdjustmentUsesTyped422()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "ZERO"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE",
                "5.000"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "ZERO"
            );

        mockMvc.perform(
                post(
                    "/api/v1/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer(
                            "inventory.adjust"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        "zero-adjustment-0001"
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        adjustmentBody(
                            stockItemId,
                            stockLocationId,
                            "0.000",
                            "INVENTORY",
                            "Zero"
                        )
                    )
            )
            .andExpect(
                status().isUnprocessableEntity()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("INVALID_STOCK_ADJUSTMENT")
            );

        assertThat(
            movementCount(
                stockItemId,
                stockLocationId
            )
        ).isZero();
    }

    @Test
    void publicAdjustmentCannotCrossTenantBoundary()
        throws Exception {

        UUID foreignOrganizationId =
            insertOrganization(
                "FOREIGN-ADJUST"
            );

        UUID foreignCategoryId =
            insertCategory(
                foreignOrganizationId,
                "Foreign Category"
            );

        UUID foreignProductId =
            insertProduct(
                foreignOrganizationId,
                foreignCategoryId,
                "FOREIGN-ADJUST",
                "Foreign Product"
            );

        UUID foreignStockItemId =
            insertStockItemForProduct(
                foreignOrganizationId,
                foreignProductId,
                "PIECE",
                "5.000"
            );

        UUID foreignCampusId =
            insertCampus(
                foreignOrganizationId,
                "FOREIGN-ADJUST"
            );

        UUID foreignLocationId =
            insertLocation(
                foreignCampusId,
                "FOREIGN-ADJUST"
            );

        UUID foreignStockLocationId =
            insertStockLocation(
                foreignLocationId,
                "Foreign Storage"
            );

        mockMvc.perform(
                post(
                    "/api/v1/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer(
                            "inventory.adjust"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        "foreign-adjustment-0001"
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        adjustmentBody(
                            foreignStockItemId,
                            foreignStockLocationId,
                            "1.000",
                            "INVENTORY",
                            "Foreign"
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("RESOURCE_NOT_FOUND")
            );

        assertThat(
            movementCount(
                foreignStockItemId,
                foreignStockLocationId
            )
        ).isZero();
    }

    @Test
    void concurrentLastUnitAdjustmentAllowsExactlyOneWinner()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "CONCURRENT"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE",
                "1.000"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "CONCURRENT"
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "1.000",
            "0.000"
        );

        String authorization =
            bearer(
                "inventory.adjust"
            );

        String body =
            adjustmentBody(
                stockItemId,
                stockLocationId,
                "-1.000",
                "INVENTORY",
                "Last unit"
            );

        CountDownLatch ready =
            new CountDownLatch(2);

        CountDownLatch start =
            new CountDownLatch(1);

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        try {

            Future<RequestOutcome> first =
                executor.submit(() ->
                    concurrentAdjust(
                        ready,
                        start,
                        authorization,
                        "last-unit-a-0001",
                        body
                    )
                );

            Future<RequestOutcome> second =
                executor.submit(() ->
                    concurrentAdjust(
                        ready,
                        start,
                        authorization,
                        "last-unit-b-0001",
                        body
                    )
                );

            boolean bothReady =
                ready.await(
                    10,
                    TimeUnit.SECONDS
                );

            assertThat(bothReady)
                .isTrue();

            start.countDown();

            RequestOutcome firstResult =
                first.get(
                    30,
                    TimeUnit.SECONDS
                );

            RequestOutcome secondResult =
                second.get(
                    30,
                    TimeUnit.SECONDS
                );

            List<RequestOutcome> results =
                List.of(
                    firstResult,
                    secondResult
                );

            long created =
                results.stream()
                    .filter(
                        result ->
                            result.status() == 201
                    )
                    .count();

            long conflicts =
                results.stream()
                    .filter(
                        result ->
                            result.status() == 409
                    )
                    .count();

            assertThat(created)
                .isEqualTo(1L);

            assertThat(conflicts)
                .isEqualTo(1L);

            RequestOutcome loser =
                results.stream()
                    .filter(
                        result ->
                            result.status() == 409
                    )
                    .findFirst()
                    .orElseThrow();

            assertThat(
                loser.body()
            ).contains(
                "OUT_OF_STOCK"
            );

        } finally {

            executor.shutdownNow();
        }

        assertBalance(
            stockItemId,
            stockLocationId,
            "0.000",
            "0.000"
        );

        assertThat(
            movementCount(
                stockItemId,
                stockLocationId
            )
        ).isEqualTo(1L);

        BigDecimal physical =
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

        BigDecimal reserved =
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

        assertThat(physical)
            .isGreaterThanOrEqualTo(
                BigDecimal.ZERO
            );

        assertThat(reserved)
            .isGreaterThanOrEqualTo(
                BigDecimal.ZERO
            );

        assertThat(reserved)
            .isLessThanOrEqualTo(
                physical
            );
    }

    private RequestOutcome concurrentAdjust(
        CountDownLatch ready,
        CountDownLatch start,
        String authorization,
        String idempotencyKey,
        String body
    ) throws Exception {

        ready.countDown();

        boolean started =
            start.await(
                10,
                TimeUnit.SECONDS
            );

        if (!started) {
            throw new IllegalStateException(
                "Concurrent start latch timed out."
            );
        }

        MvcResult result =
            mockMvc.perform(
                    post(
                        "/api/v1/inventory/adjustments"
                    )
                        .header(
                            "Authorization",
                            authorization
                        )
                        .header(
                            "Idempotency-Key",
                            idempotencyKey
                        )
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(body)
                )
                .andReturn();

        return new RequestOutcome(
            result.getResponse()
                .getStatus(),
            result.getResponse()
                .getContentAsString()
        );
    }

    private String adjustmentBody(
        UUID stockItemId,
        UUID stockLocationId,
        String quantityDelta,
        String reason,
        String comment
    ) {

        return """
            {
              "stockItemId": "%s",
              "stockLocationId": "%s",
              "quantityDelta": %s,
              "reason": "%s",
              "comment": "%s"
            }
            """.formatted(
                stockItemId,
                stockLocationId,
                quantityDelta,
                reason,
                comment
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

    private UUID insertOwnedProduct(
        String prefix
    ) {

        UUID categoryId =
            insertCategory(
                organizationId,
                prefix + " Category"
            );

        return insertProduct(
            organizationId,
            categoryId,
            prefix,
            prefix + " Product"
        );
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
            prefix + " Organization",
            "ORG" + randomSuffix()
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
            "CAMP" + randomSuffix()
        );

        return id;
    }

    private UUID insertLocation(
        UUID campusId,
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
            VALUES (
                ?,
                ?,
                ?,
                ?,
                'STORAGE',
                TRUE
            )
            """,
            id,
            campusId,
            prefix + " Location",
            "LOC" + randomSuffix()
        );

        return id;
    }

    private UUID insertCategory(
        UUID tenantId,
        String name
    ) {

        UUID id =
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
            id,
            tenantId,
            name,
            "category-" + randomSuffix()
        );

        return id;
    }

    private UUID insertProduct(
        UUID tenantId,
        UUID categoryId,
        String skuPrefix,
        String name
    ) {

        UUID id =
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
                ?,
                ?,
                ?,
                ?,
                ?,
                'PACKAGED',
                10.00,
                0.00,
                0,
                TRUE,
                FALSE,
                TRUE
            )
            """,
            id,
            tenantId,
            categoryId,
            skuPrefix
                + "-"
                + randomSuffix(),
            name
        );

        return id;
    }

    private UUID insertOwnedStockLocation(
        String prefix
    ) {

        UUID campusId =
            insertCampus(
                organizationId,
                prefix
            );

        UUID locationId =
            insertLocation(
                campusId,
                prefix
            );

        return insertStockLocation(
            locationId,
            prefix + " Storage"
        );
    }

    private UUID insertStockLocation(
        UUID locationId,
        String name
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
            VALUES (
                ?,
                ?,
                ?,
                'STORAGE',
                TRUE
            )
            """,
            id,
            locationId,
            name
        );

        return id;
    }

    private UUID insertStockItemForProduct(
        UUID tenantId,
        UUID productId,
        String baseUnit,
        String lowStockThreshold
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
                low_stock_threshold,
                track_expiry
            )
            VALUES (?, ?, ?, ?, ?, FALSE)
            """,
            id,
            tenantId,
            productId,
            baseUnit,
            new BigDecimal(
                lowStockThreshold
            )
        );

        return id;
    }

    private void insertBalance(
        UUID stockItemId,
        UUID stockLocationId,
        String physicalQuantity,
        String reservedQuantity
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
            new BigDecimal(
                physicalQuantity
            ),
            new BigDecimal(
                reservedQuantity
            )
        );
    }

    private void assertBalance(
        UUID stockItemId,
        UUID stockLocationId,
        String expectedPhysical,
        String expectedReserved
    ) {

        BigDecimal physical =
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

        BigDecimal reserved =
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

        assertThat(physical)
            .isEqualByComparingTo(
                expectedPhysical
            );

        assertThat(reserved)
            .isEqualByComparingTo(
                expectedReserved
            );
    }

    private Long movementCount(
        UUID stockItemId,
        UUID stockLocationId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_movements
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            Long.class,
            stockItemId,
            stockLocationId
        );
    }

    private String randomSuffix() {

        return UUID.randomUUID()
            .toString()
            .replace(
                "-",
                ""
            )
            .substring(
                0,
                10
            );
    }

    private record RequestOutcome(
        int status,
        String body
    ) {
    }
}