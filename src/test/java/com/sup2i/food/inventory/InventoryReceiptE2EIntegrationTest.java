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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class InventoryReceiptE2EIntegrationTest {

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
            "inventory-receipt-"
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
            "Inventory Receipt " + suffix,
            "IR" + suffix
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
            "Receipt"
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "inventory-receipt-e2e",
                InetAddress.getLoopbackAddress()
            );

        Jwt jwt =
            jwtDecoder.decode(
                tokens.accessToken()
            );

        sessionId =
            jwt.getClaimAsString("sid");
    }

    @Test
    void receiptRequiresProductWritePermission()
        throws Exception {

        UUID receiptId =
            UUID.randomUUID();

        UUID stockLocationId =
            UUID.randomUUID();

        UUID lineId =
            UUID.randomUUID();

        UUID stockItemId =
            UUID.randomUUID();

        String body =
            """
            {
              "stockLocationId": "%s",
              "lines": [
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": 1.000,
                  "unit": "PIECE"
                }
              ]
            }
            """.formatted(
                stockLocationId,
                lineId,
                stockItemId
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    receiptId
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
    void simpleReceiptCreatesCompleteInventoryChain()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "RECEIPT-SIMPLE",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "RECEIPT-SIMPLE"
            );

        UUID supplierId =
            insertSupplier(
                organizationId,
                true
            );

        UUID receiptId =
            UUID.randomUUID();

        UUID lineId =
            UUID.randomUUID();

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    receiptId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        receiptBody(
                            stockLocationId,
                            supplierId,
                            lineId,
                            stockItemId,
                            "12.000",
                            "PIECE",
                            "8.50",
                            "LOT-A",
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
            )
            .andExpect(
                jsonPath("$.receipt.id")
                    .value(receiptId.toString())
            )
            .andExpect(
                jsonPath("$.receipt.lines.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.receipt.lines[0].id")
                    .value(lineId.toString())
            );

        assertThat(
            count(
                "stock_receipts",
                "id",
                receiptId
            )
        ).isEqualTo(1L);

        assertThat(
            count(
                "stock_receipt_lines",
                "id",
                lineId
            )
        ).isEqualTo(1L);

        assertThat(
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
            )
        ).isEqualByComparingTo("12.000");

        Long movements =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM inventory_movements
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                  AND movement_type = 'PURCHASE_IN'
                  AND reference_type = 'STOCK_RECEIPT'
                  AND reference_id = ?
                  AND physical_delta = 12.000
                  AND reserved_delta = 0
                """,
                Long.class,
                stockItemId,
                stockLocationId,
                receiptId
            );

        assertThat(movements)
            .isEqualTo(1L);

        UUID movementId =
            jdbcTemplate.queryForObject(
                """
                SELECT inventory_movement_id
                FROM stock_receipt_lines
                WHERE id = ?
                """,
                UUID.class,
                lineId
            );

        UUID lotId =
            jdbcTemplate.queryForObject(
                """
                SELECT generated_lot_id
                FROM stock_receipt_lines
                WHERE id = ?
                """,
                UUID.class,
                lineId
            );

        assertThat(movementId)
            .isNotNull();

        assertThat(lotId)
            .isNotNull();

        BigDecimal remaining =
            jdbcTemplate.queryForObject(
                """
                SELECT quantity_remaining
                FROM stock_lots
                WHERE id = ?
                """,
                BigDecimal.class,
                lotId
            );

        assertThat(remaining)
            .isEqualByComparingTo("12.000");

        Long bridge =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM inventory_movement_lots
                WHERE inventory_movement_id = ?
                  AND stock_lot_id = ?
                  AND quantity_delta = 12.000
                """,
                Long.class,
                movementId,
                lotId
            );

        assertThat(bridge)
            .isEqualTo(1L);
    }

    @Test
    void identicalReceiptReplayIsIdempotent()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "REPLAY",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "REPLAY"
            );

        UUID receiptId =
            UUID.randomUUID();

        UUID lineId =
            UUID.randomUUID();

        String body =
            receiptBody(
                stockLocationId,
                null,
                lineId,
                stockItemId,
                "5.000",
                "PIECE",
                null,
                null,
                null
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    receiptId
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
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    receiptId
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

        assertBalance(
            stockItemId,
            stockLocationId,
            "5.000",
            "0.000"
        );

        assertThat(
            receiptMovementCount(receiptId)
        ).isEqualTo(1L);

        assertThat(
            receiptLotCount(receiptId)
        ).isEqualTo(1L);
    }

    @Test
    void sameReceiptIdWithDifferentPayloadConflicts()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "PAYLOAD-CONFLICT",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "PAYLOAD-CONFLICT"
            );

        UUID receiptId =
            UUID.randomUUID();

        UUID lineId =
            UUID.randomUUID();

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    receiptId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        receiptBody(
                            stockLocationId,
                            null,
                            lineId,
                            stockItemId,
                            "5.000",
                            "PIECE",
                            null,
                            null,
                            null
                        )
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    receiptId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        receiptBody(
                            stockLocationId,
                            null,
                            lineId,
                            stockItemId,
                            "6.000",
                            "PIECE",
                            null,
                            null,
                            null
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

        assertBalance(
            stockItemId,
            stockLocationId,
            "5.000",
            "0.000"
        );
    }

    @Test
    void receiptRejectsForeignStockLocation()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "FOREIGN-LOCATION",
                false
            );

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-LOCATION"
            );

        UUID foreignStockLocation =
            insertStockLocationForOrganization(
                foreignOrganization,
                "FOREIGN"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
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
                        receiptBody(
                            foreignStockLocation,
                            null,
                            UUID.randomUUID(),
                            stockItemId,
                            "1.000",
                            "PIECE",
                            null,
                            null,
                            null
                        )
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

    @Test
    void receiptRejectsForeignSupplier()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "FOREIGN-SUPPLIER",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "FOREIGN-SUPPLIER"
            );

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-SUPPLIER"
            );

        UUID supplierId =
            insertSupplier(
                foreignOrganization,
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
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
                        receiptBody(
                            stockLocationId,
                            supplierId,
                            UUID.randomUUID(),
                            stockItemId,
                            "1.000",
                            "PIECE",
                            null,
                            null,
                            null
                        )
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

    @Test
    void receiptRejectsInactiveSupplier()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "INACTIVE-SUPPLIER",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "INACTIVE-SUPPLIER"
            );

        UUID supplierId =
            insertSupplier(
                organizationId,
                false
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
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
                        receiptBody(
                            stockLocationId,
                            supplierId,
                            UUID.randomUUID(),
                            stockItemId,
                            "1.000",
                            "PIECE",
                            null,
                            null,
                            null
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            );
    }

    @Test
    void trackedExpiryRequiresExpiryDate()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "EXPIRY-REQUIRED",
                true
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "EXPIRY-REQUIRED"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
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
                        receiptBody(
                            stockLocationId,
                            null,
                            UUID.randomUUID(),
                            stockItemId,
                            "1.000",
                            "PIECE",
                            null,
                            "LOT-EXP",
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

    @Test
    void expiryDateMustBeAfterReceiptTime()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "EXPIRY-PAST",
                true
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "EXPIRY-PAST"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
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
                        receiptBody(
                            stockLocationId,
                            null,
                            UUID.randomUUID(),
                            stockItemId,
                            "1.000",
                            "PIECE",
                            null,
                            "LOT-OLD",
                            "2020-01-01T00:00:00Z"
                        )
                    )
            )
            .andExpect(
                status().isBadRequest()
            );
    }

    @Test
    void receiptUnitMustMatchStockItemBaseUnit()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "UNIT",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "UNIT"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
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
                        receiptBody(
                            stockLocationId,
                            null,
                            UUID.randomUUID(),
                            stockItemId,
                            "1.000",
                            "GRAM",
                            null,
                            null,
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

    @Test
    void duplicateLineIdsInsidePayloadAreRejected()
        throws Exception {

        UUID itemOne =
            insertOwnedProductStockItem(
                "DUP-LINE-A",
                false
            );

        UUID itemTwo =
            insertOwnedProductStockItem(
                "DUP-LINE-B",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "DUP-LINE"
            );

        UUID lineId =
            UUID.randomUUID();

        String body =
            """
            {
              "stockLocationId": "%s",
              "lines": [
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": 1.000,
                  "unit": "PIECE"
                },
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": 1.000,
                  "unit": "PIECE"
                }
              ]
            }
            """.formatted(
                stockLocationId,
                lineId,
                itemOne,
                lineId,
                itemTwo
            );

        UUID receiptId =
            UUID.randomUUID();

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    receiptId
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
                status().isBadRequest()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_ERROR")
            );

        assertThat(
            count(
                "stock_receipts",
                "id",
                receiptId
            )
        ).isZero();

        assertThat(
            balanceCount(
                itemOne,
                stockLocationId
            )
        ).isZero();

        assertThat(
            balanceCount(
                itemTwo,
                stockLocationId
            )
        ).isZero();

        assertThat(
            receiptMovementCount(
                receiptId
            )
        ).isZero();

        assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_receipt_lines
                WHERE stock_receipt_id = ?
                """,
                Long.class,
                receiptId
            )
        ).isZero();
    }

    @Test
    void receiptLineIdCannotBeReusedAcrossReceipts()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "REUSED-LINE",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "REUSED-LINE"
            );

        UUID lineId =
            UUID.randomUUID();

        String body =
            receiptBody(
                stockLocationId,
                null,
                lineId,
                stockItemId,
                "2.000",
                "PIECE",
                null,
                null,
                null
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    UUID.randomUUID()
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
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    UUID.randomUUID()
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
                status().isConflict()
            );
    }

    @Test
    void multiLineReceiptUpdatesEveryBalanceAtomically()
        throws Exception {

        UUID itemOne =
            insertOwnedProductStockItem(
                "MULTI-A",
                false
            );

        UUID itemTwo =
            insertOwnedProductStockItem(
                "MULTI-B",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "MULTI"
            );

        UUID receiptId =
            UUID.randomUUID();

        String body =
            """
            {
              "stockLocationId": "%s",
              "receiptReference": "MULTI-001",
              "lines": [
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": 3.000,
                  "unit": "PIECE"
                },
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": 7.000,
                  "unit": "PIECE"
                }
              ]
            }
            """.formatted(
                stockLocationId,
                UUID.randomUUID(),
                itemOne,
                UUID.randomUUID(),
                itemTwo
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    receiptId
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
                jsonPath("$.receipt.lines.length()")
                    .value(2)
            );

        assertBalance(
            itemOne,
            stockLocationId,
            "3.000",
            "0.000"
        );

        assertBalance(
            itemTwo,
            stockLocationId,
            "7.000",
            "0.000"
        );

        assertThat(
            receiptMovementCount(
                receiptId
            )
        ).isEqualTo(2L);

        assertThat(
            receiptLotCount(
                receiptId
            )
        ).isEqualTo(2L);
    }

    @Test
    void invalidMultiLineReceiptRollsBackEverything()
        throws Exception {

        UUID itemOne =
            insertOwnedProductStockItem(
                "ROLLBACK-A",
                false
            );

        UUID itemTwo =
            insertOwnedProductStockItem(
                "ROLLBACK-B",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "ROLLBACK"
            );

        UUID receiptId =
            UUID.randomUUID();

        String body =
            """
            {
              "stockLocationId": "%s",
              "lines": [
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": 3.000,
                  "unit": "PIECE"
                },
                {
                  "lineId": "%s",
                  "stockItemId": "%s",
                  "quantity": 7.000,
                  "unit": "GRAM"
                }
              ]
            }
            """.formatted(
                stockLocationId,
                UUID.randomUUID(),
                itemOne,
                UUID.randomUUID(),
                itemTwo
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    receiptId
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
                status().isBadRequest()
            );

        assertThat(
            count(
                "stock_receipts",
                "id",
                receiptId
            )
        ).isZero();

        assertThat(
            receiptMovementCount(
                receiptId
            )
        ).isZero();

        assertThat(
            balanceCount(
                itemOne,
                stockLocationId
            )
        ).isZero();

        assertThat(
            balanceCount(
                itemTwo,
                stockLocationId
            )
        ).isZero();
    }

    @Test
    void getReceiptReturnsPersistedReceipt()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "GET-RECEIPT",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "GET-RECEIPT"
            );

        UUID receiptId =
            UUID.randomUUID();

        UUID lineId =
            UUID.randomUUID();

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    receiptId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        receiptBody(
                            stockLocationId,
                            null,
                            lineId,
                            stockItemId,
                            "4.000",
                            "PIECE",
                            null,
                            null,
                            null
                        )
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    receiptId
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
                jsonPath("$.id")
                    .value(receiptId.toString())
            )
            .andExpect(
                jsonPath("$.lines[0].id")
                    .value(lineId.toString())
            );
    }

    @Test
    void foreignReceiptIsHidden()
        throws Exception {

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-RECEIPT"
            );

        UUID foreignUser =
            insertUser(
                foreignOrganization
            );

        UUID foreignStockLocation =
            insertStockLocationForOrganization(
                foreignOrganization,
                "FOREIGN-R"
            );

        UUID receiptId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_receipts (
                id,
                stock_location_id,
                received_at,
                received_by,
                status
            )
            VALUES (
                ?,
                ?,
                CURRENT_TIMESTAMP,
                ?,
                'RECEIVED'
            )
            """,
            receiptId,
            foreignStockLocation,
            foreignUser
        );

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    receiptId
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
    void getLotReturnsOwnedLot()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "GET-LOT",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "GET-LOT"
            );

        UUID receiptId =
            UUID.randomUUID();

        UUID lineId =
            UUID.randomUUID();

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
                    receiptId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        receiptBody(
                            stockLocationId,
                            null,
                            lineId,
                            stockItemId,
                            "3.000",
                            "PIECE",
                            null,
                            "LOT-GET",
                            null
                        )
                    )
            )
            .andExpect(
                status().isOk()
            );

        UUID lotId =
            jdbcTemplate.queryForObject(
                """
                SELECT generated_lot_id
                FROM stock_receipt_lines
                WHERE id = ?
                """,
                UUID.class,
                lineId
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/lots/{lotId}",
                    lotId
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
                jsonPath("$.id")
                    .value(lotId.toString())
            )
            .andExpect(
                jsonPath("$.lotNumber")
                    .value("LOT-GET")
            );
    }

    @Test
    void foreignLotIsHidden()
        throws Exception {

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-LOT"
            );

        UUID categoryId =
            insertCategory(
                foreignOrganization
            );

        UUID productId =
            insertProduct(
                foreignOrganization,
                categoryId,
                "FOREIGN-LOT"
            );

        UUID stockItemId =
            insertStockItem(
                foreignOrganization,
                productId,
                false
            );

        UUID stockLocationId =
            insertStockLocationForOrganization(
                foreignOrganization,
                "FOREIGN-LOT"
            );

        UUID lotId =
            insertLot(
                stockItemId,
                stockLocationId,
                "FOREIGN",
                "1.000",
                "1.000",
                "2030-01-01T00:00:00Z"
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/lots/{lotId}",
                    lotId
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
    void lotSearchDefaultsToRemainingOnlyAndOrdersByFefo()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "FEFO",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "FEFO"
            );

        UUID laterLot =
            insertLot(
                stockItemId,
                stockLocationId,
                "LATER",
                "2.000",
                "2.000",
                "2031-01-01T00:00:00Z"
            );

        UUID exhaustedLot =
            insertLot(
                stockItemId,
                stockLocationId,
                "EXHAUSTED",
                "1.000",
                "0.000",
                "2029-01-01T00:00:00Z"
            );

        UUID earlierLot =
            insertLot(
                stockItemId,
                stockLocationId,
                "EARLIER",
                "3.000",
                "3.000",
                "2030-01-01T00:00:00Z"
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/lots"
                )
                    .queryParam(
                        "stockItemId",
                        stockItemId.toString()
                    )
                    .queryParam(
                        "stockLocationId",
                        stockLocationId.toString()
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
                jsonPath("$.length()")
                    .value(2)
            )
            .andExpect(
                jsonPath("$[0].id")
                    .value(earlierLot.toString())
            )
            .andExpect(
                jsonPath("$[1].id")
                    .value(laterLot.toString())
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/lots"
                )
                    .queryParam(
                        "stockItemId",
                        stockItemId.toString()
                    )
                    .queryParam(
                        "stockLocationId",
                        stockLocationId.toString()
                    )
                    .queryParam(
                        "remainingOnly",
                        "false"
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
                jsonPath("$.length()")
                    .value(3)
            )
            .andExpect(
                jsonPath("$[0].id")
                    .value(exhaustedLot.toString())
            );
    }

    @Test
    void receiptRejectsStockSubjectWithTrackingDisabled()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "TRACKING-DISABLED",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "TRACKING-DISABLED"
            );

        jdbcTemplate.update(
            """
            UPDATE products
            SET track_stock = FALSE
            WHERE id = (
                SELECT product_id
                FROM stock_items
                WHERE id = ?
            )
            """,
            stockItemId
        );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
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
                        receiptBody(
                            stockLocationId,
                            null,
                            UUID.randomUUID(),
                            stockItemId,
                            "1.000",
                            "PIECE",
                            null,
                            null,
                            null
                        )
                    )
            )
            .andExpect(
                status().isBadRequest()
            );
    }

    @Test
    void unitCostWithMoreThanTwoDecimalsIsRejected()
        throws Exception {

        UUID stockItemId =
            insertOwnedProductStockItem(
                "COST-SCALE",
                false
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "COST-SCALE"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/receipts/{receiptId}",
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
                        receiptBody(
                            stockLocationId,
                            null,
                            UUID.randomUUID(),
                            stockItemId,
                            "1.000",
                            "PIECE",
                            "1.234",
                            null,
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

    private String receiptBody(
        UUID stockLocationId,
        UUID supplierId,
        UUID lineId,
        UUID stockItemId,
        String quantity,
        String unit,
        String unitCost,
        String lotNumber,
        String expiresAt
    ) {

        StringBuilder json =
            new StringBuilder();

        json.append("{\n");

        json.append(
            "  \"stockLocationId\": \""
        )
            .append(stockLocationId)
            .append("\",\n");

        if (supplierId != null) {

            json.append(
                "  \"supplierId\": \""
            )
                .append(supplierId)
                .append("\",\n");
        }

        json.append(
            "  \"receiptReference\": \"RCPT-E2E\",\n"
        );

        json.append(
            "  \"notes\": \"Inventory receipt E2E\",\n"
        );

        json.append(
            "  \"lines\": [\n"
        );

        json.append(
            "    {\n"
        );

        json.append(
            "      \"lineId\": \""
        )
            .append(lineId)
            .append("\",\n");

        json.append(
            "      \"stockItemId\": \""
        )
            .append(stockItemId)
            .append("\",\n");

        json.append(
            "      \"quantity\": "
        )
            .append(quantity)
            .append(",\n");

        json.append(
            "      \"unit\": \""
        )
            .append(unit)
            .append("\"");

        if (unitCost != null) {

            json.append(
                ",\n      \"unitCost\": "
            )
                .append(unitCost);
        }

        if (lotNumber != null) {

            json.append(
                ",\n      \"lotNumber\": \""
            )
                .append(lotNumber)
                .append("\"");
        }

        if (expiresAt != null) {

            json.append(
                ",\n      \"expiresAt\": \""
            )
                .append(expiresAt)
                .append("\"");
        }

        json.append(
            "\n    }\n"
        );

        json.append(
            "  ]\n"
        );

        json.append(
            "}\n"
        );

        return json.toString();
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

    private UUID insertOwnedProductStockItem(
        String prefix,
        boolean trackExpiry
    ) {

        UUID categoryId =
            insertCategory(
                organizationId
            );

        UUID productId =
            insertProduct(
                organizationId,
                categoryId,
                prefix
            );

        return insertStockItem(
            organizationId,
            productId,
            trackExpiry
        );
    }

    private UUID insertCategory(
        UUID tenantId
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
            "Category " + randomSuffix(),
            "category-" + randomSuffix()
        );

        return id;
    }

    private UUID insertProduct(
        UUID tenantId,
        UUID categoryId,
        String prefix
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
            id,
            tenantId,
            categoryId,
            prefix + "-" + randomSuffix(),
            prefix + " Product"
        );

        return id;
    }

    private UUID insertStockItem(
        UUID tenantId,
        UUID productId,
        boolean trackExpiry
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
            VALUES (?, ?, ?, 'PIECE', ?)
            """,
            id,
            tenantId,
            productId,
            trackExpiry
        );

        return id;
    }

    private UUID insertOwnedStockLocation(
        String prefix
    ) {

        return insertStockLocationForOrganization(
            organizationId,
            prefix
        );
    }

    private UUID insertStockLocationForOrganization(
        UUID tenantId,
        String prefix
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
            VALUES (?, ?, ?, 'STORAGE', TRUE)
            """,
            stockLocationId,
            locationId,
            prefix + " Stock"
        );

        return stockLocationId;
    }

    private UUID insertSupplier(
        UUID tenantId,
        boolean active
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO suppliers (
                id,
                organization_id,
                name,
                is_active
            )
            VALUES (?, ?, ?, ?)
            """,
            id,
            tenantId,
            "Supplier " + randomSuffix(),
            active
        );

        return id;
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
            VALUES (?, ?, ?, 'Foreign', 'User', 'ACTIVE')
            """,
            id,
            tenantId,
            "foreign-"
                + randomSuffix()
                + "@sup2i.test"
        );

        return id;
    }

    private UUID insertLot(
        UUID stockItemId,
        UUID stockLocationId,
        String lotNumber,
        String received,
        String remaining,
        String expiresAt
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
                ?,
                ?
            )
            """,
            id,
            stockItemId,
            stockLocationId,
            lotNumber,
            OffsetDateTime.parse(
                expiresAt
            ),
            new BigDecimal(received),
            new BigDecimal(remaining)
        );

        return id;
    }

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

    private Long balanceCount(
        UUID stockItemId,
        UUID stockLocationId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM stock_balances
            WHERE stock_item_id = ?
              AND stock_location_id = ?
            """,
            Long.class,
            stockItemId,
            stockLocationId
        );
    }

    private Long receiptMovementCount(
        UUID receiptId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_movements
            WHERE reference_type = 'STOCK_RECEIPT'
              AND reference_id = ?
            """,
            Long.class,
            receiptId
        );
    }

    private Long receiptLotCount(
        UUID receiptId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM stock_receipt_lines
            WHERE stock_receipt_id = ?
              AND generated_lot_id IS NOT NULL
            """,
            Long.class,
            receiptId
        );
    }

    private Long count(
        String table,
        String column,
        UUID value
    ) {

        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM "
                + table
                + " WHERE "
                + column
                + " = ?",
            Long.class,
            value
        );
    }

    private String randomSuffix() {

        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10);
    }
}