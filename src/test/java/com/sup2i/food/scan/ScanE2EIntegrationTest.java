package com.sup2i.food.scan;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.scan.service.ScanTokenHasher;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private ScanTokenHasher tokenHasher;

    private UUID organizationId;
    private UUID campusId;
    private UUID locationId;
    private UUID userId;
    private String email;
    private String sessionId;

    @BeforeEach
    void seedTenantAndSession() {

        String suffix =
            randomSuffix();

        organizationId =
            UUID.randomUUID();

        campusId =
            UUID.randomUUID();

        locationId =
            UUID.randomUUID();

        userId =
            UUID.randomUUID();

        email =
            "scan-"
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
            "Scan Organization " + suffix,
            "SCORG" + suffix
        );

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
            organizationId,
            "Scan Campus " + suffix,
            "SCC" + suffix
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
            VALUES (?, ?, ?, ?, 'SNACK', TRUE)
            """,
            locationId,
            campusId,
            "Scan Snack " + suffix,
            "SCL" + suffix
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
            "Scan",
            "Cashier"
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
                "scan-e2e",
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

        assertThat(
            sessionId
        ).isNotBlank();
    }

    @Test
    void resolveRequiresAuthentication()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "rawValue": "NO-AUTH"
                        }
                        """
                    )
            )
            .andExpect(
                status().isUnauthorized()
            );
    }

    @Test
    void resolveRejectsBlankRawValue()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "rawValue": "   "
                        }
                        """
                    )
            )
            .andExpect(
                status().isBadRequest()
            );
    }

    @Test
    void productBarcodeResolvesWithoutScanSpecificPermission()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "BASIC",
                false
            );

        String barcode =
            barcode(
                "BASIC"
            );

        insertBarcode(
            productId,
            null,
            barcode,
            "1.000"
        );

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        scanBody(
                            barcode,
                            null
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.type")
                    .value("PRODUCT")
            )
            .andExpect(
                jsonPath("$.product.id")
                    .value(
                        productId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.product.barcode")
                    .value(barcode)
            )
            .andExpect(
                jsonPath("$.product.available")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.product.active")
                    .value(true)
            );

        Map<String, Object> event =
            latestEvent(
                barcode
            );

        assertThat(
            event.get("scan_type")
        ).isEqualTo(
            "PRODUCT_BARCODE"
        );

        assertThat(
            event.get("result")
        ).isEqualTo(
            "SUCCESS"
        );

        assertThat(
            event.get(
                "resolved_reference_id"
            )
        ).isEqualTo(
            productId
        );

        assertThat(
            event.get("error_code")
        ).isNull();
    }

    @Test
    void variantBarcodeReturnsVariantAndPackQuantity()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "VARIANT",
                false
            );

        UUID variantId =
            insertVariant(
                productId,
                "VARIANT"
            );

        String barcode =
            barcode(
                "VARIANT"
            );

        insertBarcode(
            productId,
            variantId,
            barcode,
            "6.000"
        );

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        scanBody(
                            barcode,
                            null
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.type")
                    .value("PRODUCT")
            )
            .andExpect(
                jsonPath("$.product.variantId")
                    .value(
                        variantId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.product.variantName")
                    .value(
                        "VARIANT Variant"
                    )
            )
            .andExpect(
                jsonPath("$.product.packQuantity")
                    .value(6.0)
            );
    }

    @Test
    void trackedProductAvailabilityUsesTerminalLocationAndReservedStock()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "TERMINAL-STOCK",
                true
            );

        String barcode =
            barcode(
                "TERMINAL-STOCK"
            );

        insertBarcode(
            productId,
            null,
            barcode,
            "3.000"
        );

        UUID stockItemId =
            insertProductStockItem(
                organizationId,
                productId
            );

        UUID mainStockLocation =
            insertStockLocation(
                locationId,
                "MAIN"
            );

        insertBalance(
            stockItemId,
            mainStockLocation,
            "5.000",
            "3.000"
        );

        UUID otherLocation =
            insertLocation(
                campusId,
                "OTHER"
            );

        UUID otherStockLocation =
            insertStockLocation(
                otherLocation,
                "OTHER"
            );

        insertBalance(
            stockItemId,
            otherStockLocation,
            "100.000",
            "0.000"
        );

        UUID terminalId =
            insertTerminal(
                locationId,
                "STOCK"
            );

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        scanBody(
                            barcode,
                            terminalId
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.product.available")
                    .value(false)
            );
    }

    @Test
    void variantStockFallsBackToProductStock()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "FALLBACK",
                true
            );

        UUID variantId =
            insertVariant(
                productId,
                "FALLBACK"
            );

        String barcode =
            barcode(
                "FALLBACK"
            );

        insertBarcode(
            productId,
            variantId,
            barcode,
            "2.000"
        );

        UUID productStockItem =
            insertProductStockItem(
                organizationId,
                productId
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                "FALLBACK"
            );

        insertBalance(
            productStockItem,
            stockLocationId,
            "5.000",
            "0.000"
        );

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        scanBody(
                            barcode,
                            null
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.product.variantId")
                    .value(
                        variantId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.product.available")
                    .value(true)
            );
    }

    @Test
    void foreignTenantBarcodeDoesNotResolve()
        throws Exception {

        Tenant foreign =
            insertTenant(
                "FOREIGN-BARCODE"
            );

        UUID productId =
            insertProduct(
                foreign.organizationId(),
                "FOREIGN-BARCODE",
                false
            );

        String barcode =
            barcode(
                "FOREIGN-BARCODE"
            );

        insertBarcode(
            productId,
            null,
            barcode,
            "1.000"
        );

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        scanBody(
                            barcode,
                            null
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "RESOURCE_NOT_FOUND"
                    )
            );

        Map<String, Object> event =
            latestEvent(
                barcode
            );

        assertThat(
            event.get("scan_type")
        ).isEqualTo(
            "UNKNOWN"
        );

        assertThat(
            event.get("result")
        ).isEqualTo(
            "UNKNOWN"
        );

        assertThat(
            event.get(
                "resolved_reference_id"
            )
        ).isNull();
    }

    @Test
    void foreignTerminalReturnsResourceNotFoundAndAuditsWithoutTerminalLeak()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "FOREIGN-TERMINAL-PRODUCT",
                false
            );

        String barcode =
            barcode(
                "FOREIGN-TERMINAL-PRODUCT"
            );

        insertBarcode(
            productId,
            null,
            barcode,
            "1.000"
        );

        Tenant foreign =
            insertTenant(
                "FOREIGN-TERMINAL"
            );

        UUID foreignTerminal =
            insertTerminal(
                foreign.locationId(),
                "FOREIGN"
            );

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        scanBody(
                            barcode,
                            foreignTerminal
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "RESOURCE_NOT_FOUND"
                    )
            );

        Map<String, Object> event =
            latestEvent(
                barcode
            );

        assertThat(
            event.get("terminal_id")
        ).isNull();

        assertThat(
            event.get("result")
        ).isEqualTo(
            "ERROR"
        );
    }

    @Test
    void orderQrResolvesCurrentOrderSnapshotWithoutStudentOwnership()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "ORDER",
                false
            );

        UUID orderId =
            insertOrder(
                organizationId,
                campusId,
                locationId,
                productId,
                "AWAITING_PAYMENT"
            );

        String rawValue =
            qr(
                "ORDER"
            );

        insertCredential(
            "ORDER",
            orderId,
            rawValue,
            "ACTIVE",
            OffsetDateTime
                .now()
                .plusMinutes(15)
        );

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        scanBody(
                            rawValue,
                            null
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.type")
                    .value("ORDER")
            )
            .andExpect(
                jsonPath("$.order.id")
                    .value(
                        orderId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.order.status")
                    .value(
                        "AWAITING_PAYMENT"
                    )
            )
            .andExpect(
                jsonPath("$.order.paymentStatus")
                    .value(
                        "PENDING"
                    )
            )
            .andExpect(
                jsonPath("$.order.total")
                    .value(25.0)
            )
            .andExpect(
                jsonPath("$.order.items.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath(
                    "$.order.items[0].quantity"
                )
                    .value(2)
            );

        Map<String, Object> event =
            latestEvent(
                rawValue
            );

        assertThat(
            event.get("scan_type")
        ).isEqualTo(
            "ORDER"
        );

        assertThat(
            event.get("result")
        ).isEqualTo(
            "SUCCESS"
        );

        assertThat(
            event.get(
                "resolved_reference_id"
            )
        ).isEqualTo(
            orderId
        );
    }

    @Test
    void revokedOrderQrReturnsQrRevokedAndAuditsRefusal()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "REVOKED",
                false
            );

        UUID orderId =
            insertOrder(
                organizationId,
                campusId,
                locationId,
                productId,
                "AWAITING_PAYMENT"
            );

        String rawValue =
            qr(
                "REVOKED"
            );

        insertCredential(
            "ORDER",
            orderId,
            rawValue,
            "REVOKED",
            null
        );

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        scanBody(
                            rawValue,
                            null
                        )
                    )
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "QR_REVOKED"
                    )
            );

        assertRefusedOrderEvent(
            rawValue,
            orderId,
            "QR_REVOKED"
        );
    }

    @Test
    void expiredOrderQrReturnsQrExpiredAndAuditsRefusal()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "EXPIRED",
                false
            );

        UUID orderId =
            insertOrder(
                organizationId,
                campusId,
                locationId,
                productId,
                "AWAITING_PAYMENT"
            );

        String rawValue =
            qr(
                "EXPIRED"
            );

        insertCredential(
            "ORDER",
            orderId,
            rawValue,
            "EXPIRED",
            null
        );

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        scanBody(
                            rawValue,
                            null
                        )
                    )
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "QR_EXPIRED"
                    )
            );

        assertRefusedOrderEvent(
            rawValue,
            orderId,
            "QR_EXPIRED"
        );
    }

    @Test
    void usedOrderQrReturnsInvalidQrAndAuditsRefusal()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "USED",
                false
            );

        UUID orderId =
            insertOrder(
                organizationId,
                campusId,
                locationId,
                productId,
                "AWAITING_PAYMENT"
            );

        String rawValue =
            qr(
                "USED"
            );

        insertCredential(
            "ORDER",
            orderId,
            rawValue,
            "USED",
            null
        );

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        scanBody(
                            rawValue,
                            null
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "INVALID_QR"
                    )
            );

        assertRefusedOrderEvent(
            rawValue,
            orderId,
            "INVALID_QR"
        );
    }

    @Test
    void foreignTenantOrderQrDoesNotLeakCredentialState()
        throws Exception {

        Tenant foreign =
            insertTenant(
                "FOREIGN-ORDER"
            );

        UUID productId =
            insertProduct(
                foreign.organizationId(),
                "FOREIGN-ORDER",
                false
            );

        UUID orderId =
            insertOrder(
                foreign.organizationId(),
                foreign.campusId(),
                foreign.locationId(),
                productId,
                "AWAITING_PAYMENT"
            );

        String rawValue =
            qr(
                "FOREIGN-ORDER"
            );

        insertCredential(
            "ORDER",
            orderId,
            rawValue,
            "REVOKED",
            null
        );

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        scanBody(
                            rawValue,
                            null
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "RESOURCE_NOT_FOUND"
                    )
            );

        Map<String, Object> event =
            latestEvent(
                rawValue
            );

        assertThat(
            event.get("scan_type")
        ).isEqualTo(
            "UNKNOWN"
        );

        assertThat(
            event.get("error_code")
        ).isEqualTo(
            "RESOURCE_NOT_FOUND"
        );
    }

    @Test
    void unknownScanReturnsResourceNotFoundAndAuditsUnknown()
        throws Exception {

        String rawValue =
            "UNKNOWN-"
                + randomSuffix();

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        scanBody(
                            rawValue,
                            null
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "RESOURCE_NOT_FOUND"
                    )
            );

        Map<String, Object> event =
            latestEvent(
                rawValue
            );

        assertThat(
            event.get("scan_type")
        ).isEqualTo(
            "UNKNOWN"
        );

        assertThat(
            event.get("result")
        ).isEqualTo(
            "UNKNOWN"
        );

        assertThat(
            event.get("error_code")
        ).isEqualTo(
            "RESOURCE_NOT_FOUND"
        );

        assertThat(
            event.get("token_fingerprint")
        ).isEqualTo(
            tokenHasher.hash(
                rawValue
            )
        );
    }

    @Test
    void foodPassCredentialIsNotResolvedInP2()
        throws Exception {

        String rawValue =
            qr(
                "FOOD-PASS"
            );

        insertCredential(
            "FOOD_PASS",
            UUID.randomUUID(),
            rawValue,
            "ACTIVE",
            null
        );

        mockMvc.perform(
                post(
                    "/api/v1/scan/resolve"
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        scanBody(
                            rawValue,
                            null
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "RESOURCE_NOT_FOUND"
                    )
            );

        Map<String, Object> event =
            latestEvent(
                rawValue
            );

        assertThat(
            event.get("scan_type")
        ).isEqualTo(
            "UNKNOWN"
        );

        assertThat(
            event.get(
                "resolved_reference_id"
            )
        ).isNull();
    }

    private void assertRefusedOrderEvent(
        String rawValue,
        UUID orderId,
        String errorCode
    ) {

        Map<String, Object> event =
            latestEvent(
                rawValue
            );

        assertThat(
            event.get("scan_type")
        ).isEqualTo(
            "ORDER"
        );

        assertThat(
            event.get("result")
        ).isEqualTo(
            "REFUSED"
        );

        assertThat(
            event.get(
                "resolved_reference_id"
            )
        ).isEqualTo(
            orderId
        );

        assertThat(
            event.get("error_code")
        ).isEqualTo(
            errorCode
        );
    }

    private Map<String, Object> latestEvent(
        String rawValue
    ) {

        return jdbcTemplate.queryForMap(
            """
            SELECT
                terminal_id,
                scan_type,
                result,
                resolved_reference_id,
                token_fingerprint,
                error_code
            FROM scan_events
            WHERE operator_id = ?
              AND token_fingerprint = ?
            ORDER BY occurred_at DESC, id DESC
            LIMIT 1
            """,
            userId,
            tokenHasher.hash(
                rawValue
            )
        );
    }

    private String scanBody(
        String rawValue,
        UUID terminalId
    ) {

        if (terminalId == null) {

            return """
                {
                  "rawValue": "%s"
                }
                """.formatted(
                    rawValue
                );
        }

        return """
            {
              "rawValue": "%s",
              "terminalId": "%s"
            }
            """.formatted(
                rawValue,
                terminalId
            );
    }

    private String bearer(
        String... permissions
    ) {

        return "Bearer "
            + token(
                permissions
            );
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
                    List.of(
                        permissions
                    )
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

    private Tenant insertTenant(
        String prefix
    ) {

        UUID organization =
            UUID.randomUUID();

        UUID campus =
            UUID.randomUUID();

        UUID location =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

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
            organization,
            prefix + " Organization",
            "FGO" + suffix
        );

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
            campus,
            organization,
            prefix + " Campus",
            "FGC" + suffix
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
            VALUES (?, ?, ?, ?, 'SNACK', TRUE)
            """,
            location,
            campus,
            prefix + " Location",
            "FGL" + suffix
        );

        return new Tenant(
            organization,
            campus,
            location
        );
    }

    private UUID insertLocation(
        UUID tenantCampusId,
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

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
            tenantCampusId,
            prefix + " Location",
            "LOC" + suffix
        );

        return id;
    }

    private UUID insertProduct(
        UUID tenantOrganizationId,
        String prefix,
        boolean trackStock
    ) {

        UUID categoryId =
            UUID.randomUUID();

        UUID productId =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

        jdbcTemplate.update(
            """
            INSERT INTO categories (
                id,
                organization_id,
                name,
                slug,
                is_active,
                display_order
            )
            VALUES (?, ?, ?, ?, TRUE, 0)
            """,
            categoryId,
            tenantOrganizationId,
            prefix + " Category",
            (
                "scan-"
                    + suffix
            ).toLowerCase(
                Locale.ROOT
            )
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
                track_stock,
                is_prepared,
                is_active
            )
            VALUES (
                ?, ?, ?, ?, ?,
                'PACKAGED',
                12.50,
                0,
                ?,
                FALSE,
                TRUE
            )
            """,
            productId,
            tenantOrganizationId,
            categoryId,
            "SKU-" + suffix,
            prefix + " Product",
            trackStock
        );

        return productId;
    }

    private UUID insertVariant(
        UUID productId,
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO product_variants (
                id,
                product_id,
                name,
                sku,
                price_delta,
                is_active,
                display_order
            )
            VALUES (?, ?, ?, ?, 0, TRUE, 0)
            """,
            id,
            productId,
            prefix + " Variant",
            "VAR-" + randomSuffix()
        );

        return id;
    }

    private void insertBarcode(
        UUID productId,
        UUID variantId,
        String value,
        String packQuantity
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO product_barcodes (
                id,
                product_id,
                variant_id,
                barcode,
                pack_quantity,
                is_primary,
                is_active
            )
            VALUES (?, ?, ?, ?, ?, FALSE, TRUE)
            """,
            UUID.randomUUID(),
            productId,
            variantId,
            value,
            new BigDecimal(
                packQuantity
            )
        );
    }

    private UUID insertProductStockItem(
        UUID tenantOrganizationId,
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
            tenantOrganizationId,
            productId
        );

        return id;
    }

    private UUID insertStockLocation(
        UUID tenantLocationId,
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
            VALUES (?, ?, ?, 'COUNTER', TRUE)
            """,
            id,
            tenantLocationId,
            prefix + " Stock"
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
            new BigDecimal(
                physical
            ),
            new BigDecimal(
                reserved
            )
        );
    }

    private UUID insertTerminal(
        UUID tenantLocationId,
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO pos_terminals (
                id,
                location_id,
                code,
                name,
                software_type,
                is_active,
                terminal_type
            )
            VALUES (
                ?, ?, ?, ?,
                'SUP2I_POS',
                TRUE,
                'POS'
            )
            """,
            id,
            tenantLocationId,
            "TERM-" + randomSuffix(),
            prefix + " Terminal"
        );

        return id;
    }

    private UUID insertOrder(
        UUID tenantOrganizationId,
        UUID tenantCampusId,
        UUID tenantLocationId,
        UUID productId,
        String status
    ) {

        UUID orderId =
            UUID.randomUUID();

        UUID itemId =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

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
                tax_total,
                discount_total,
                total,
                currency,
                payment_expires_at,
                order_type,
                payment_status,
                customer_note
            )
            VALUES (
                ?, ?, ?, ?, NULL,
                ?,
                CURRENT_DATE,
                'MOBILE',
                ?,
                25.00,
                0.00,
                0.00,
                25.00,
                'MAD',
                CURRENT_TIMESTAMP
                    + INTERVAL '15 minutes',
                'MOBILE_SNACK',
                'PENDING',
                'Scan E2E'
            )
            """,
            orderId,
            tenantOrganizationId,
            tenantCampusId,
            tenantLocationId,
            "S-" + suffix,
            status
        );

        jdbcTemplate.update(
            """
            INSERT INTO order_items (
                id,
                order_id,
                product_id,
                variant_id,
                product_name_snapshot,
                variant_name_snapshot,
                sku_snapshot,
                unit_price,
                quantity,
                discount_amount,
                line_total,
                tax_rate_snapshot,
                line_tax,
                special_instructions
            )
            VALUES (
                ?, ?, ?, NULL,
                ?,
                NULL,
                ?,
                12.50,
                2,
                0.00,
                25.00,
                0.00,
                0.00,
                NULL
            )
            """,
            itemId,
            orderId,
            productId,
            "Order Product " + suffix,
            "ORDER-SKU-" + suffix
        );

        return orderId;
    }

    private void insertCredential(
        String credentialType,
        UUID subjectId,
        String rawValue,
        String status,
        OffsetDateTime expiresAt
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO qr_credentials (
                id,
                credential_type,
                subject_id,
                token_hash,
                status,
                expires_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            credentialType,
            subjectId,
            tokenHasher.hash(
                rawValue
            ),
            status,
            expiresAt
        );
    }

    private String barcode(
        String prefix
    ) {

        return "BC-"
            + prefix
            + "-"
            + randomSuffix();
    }

    private String qr(
        String prefix
    ) {

        return "QR-"
            + prefix
            + "-"
            + UUID.randomUUID();
    }

    private String randomSuffix() {

        return UUID
            .randomUUID()
            .toString()
            .replace(
                "-",
                ""
            )
            .substring(
                0,
                10
            )
            .toUpperCase(
                Locale.ROOT
            );
    }

    private record Tenant(
        UUID organizationId,
        UUID campusId,
        UUID locationId
    ) {
    }
}