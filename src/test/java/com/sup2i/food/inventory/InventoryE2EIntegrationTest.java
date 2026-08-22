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
class InventoryE2EIntegrationTest {

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
            "inventory-"
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
            "Inventory " + suffix,
            "INV" + suffix
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
            "Manager"
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "inventory-e2e",
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

    // =========================================================
    // SECURITY / STOCK LOCATION
    // =========================================================

    @Test
    void inventoryRequiresProductWritePermission()
        throws Exception {

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/stock-items"
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
    void createStockLocationForOwnedLocation()
        throws Exception {

        UUID campusId =
            insertCampus(
                organizationId,
                "MAIN"
            );

        UUID locationId =
            insertLocation(
                campusId,
                "STORE",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/stock-locations"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "locationId": "%s",
                          "name": "Main Storage",
                          "type": "STORAGE",
                          "active": true
                        }
                        """.formatted(
                            locationId
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.locationId")
                    .value(locationId.toString())
            )
            .andExpect(
                jsonPath("$.name")
                    .value("Main Storage")
            )
            .andExpect(
                jsonPath("$.type")
                    .value("STORAGE")
            )
            .andExpect(
                jsonPath("$.active")
                    .value(true)
            );

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_locations
                WHERE location_id = ?
                  AND name = 'Main Storage'
                """,
                Long.class,
                locationId
            );

        assertThat(count)
            .isEqualTo(1L);
    }

    @Test
    void createStockLocationCannotCrossTenantBoundary()
        throws Exception {

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-LOCATION"
            );

        UUID foreignCampus =
            insertCampus(
                foreignOrganization,
                "FOREIGN"
            );

        UUID foreignLocation =
            insertLocation(
                foreignCampus,
                "FOREIGN",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/stock-locations"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "locationId": "%s",
                          "name": "Foreign Storage",
                          "type": "STORAGE",
                          "active": true
                        }
                        """.formatted(
                            foreignLocation
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

    // =========================================================
    // STOCK ITEMS
    // =========================================================

    @Test
    void createProductStockItem()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "STOCK-PRODUCT"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/stock-items"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "productId": "%s",
                          "baseUnit": "PIECE",
                          "lowStockThreshold": 5.000,
                          "trackExpiry": true
                        }
                        """.formatted(
                            productId
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.subjectType")
                    .value("PRODUCT")
            )
            .andExpect(
                jsonPath("$.productId")
                    .value(productId.toString())
            )
            .andExpect(
                jsonPath("$.baseUnit")
                    .value("PIECE")
            )
            .andExpect(
                jsonPath("$.trackExpiry")
                    .value(true)
            );

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_items
                WHERE organization_id = ?
                  AND product_id = ?
                """,
                Long.class,
                organizationId,
                productId
            );

        assertThat(count)
            .isEqualTo(1L);
    }

    @Test
    void createVariantStockItem()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "STOCK-VARIANT"
            );

        UUID variantId =
            insertVariant(
                productId,
                "Large"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/stock-items"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "variantId": "%s",
                          "baseUnit": "PIECE",
                          "trackExpiry": false
                        }
                        """.formatted(
                            variantId
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.subjectType")
                    .value("VARIANT")
            )
            .andExpect(
                jsonPath("$.variantId")
                    .value(variantId.toString())
            )
            .andExpect(
                jsonPath("$.baseUnit")
                    .value("PIECE")
            );
    }

    @Test
    void createIngredientStockItem()
        throws Exception {

        UUID ingredientId =
            insertIngredient(
                organizationId,
                "FLOUR",
                "Flour",
                "GRAM",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/stock-items"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "ingredientId": "%s",
                          "baseUnit": "GRAM",
                          "lowStockThreshold": 1000.000,
                          "trackExpiry": true
                        }
                        """.formatted(
                            ingredientId
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.subjectType")
                    .value("INGREDIENT")
            )
            .andExpect(
                jsonPath("$.ingredientId")
                    .value(ingredientId.toString())
            )
            .andExpect(
                jsonPath("$.baseUnit")
                    .value("GRAM")
            );
    }

    @Test
    void stockItemRequiresExactlyOneSubject()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/stock-items"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "baseUnit": "PIECE",
                          "trackExpiry": false
                        }
                        """
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
    void duplicateStockSubjectReturnsConflict()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "DUPLICATE-STOCK"
            );

        String request =
            """
            {
              "productId": "%s",
              "baseUnit": "PIECE",
              "trackExpiry": false
            }
            """.formatted(
                productId
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/stock-items"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(request)
            )
            .andExpect(
                status().isCreated()
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/stock-items"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(request)
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );
    }

    @Test
    void ingredientStockUnitMustMatchIngredientBaseUnit()
        throws Exception {

        UUID ingredientId =
            insertIngredient(
                organizationId,
                "SUGAR",
                "Sugar",
                "GRAM",
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/stock-items"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "ingredientId": "%s",
                          "baseUnit": "KILOGRAM",
                          "trackExpiry": false
                        }
                        """.formatted(
                            ingredientId
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
    void updateStockItemConfiguration()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "UPDATE-STOCK"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE"
            );

        mockMvc.perform(
                put(
                    "/api/v1/admin/inventory/stock-items/{stockItemId}",
                    stockItemId
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "lowStockThreshold": 7.500,
                          "trackExpiry": true
                        }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.trackExpiry")
                    .value(true)
            );

        BigDecimal threshold =
            jdbcTemplate.queryForObject(
                """
                SELECT low_stock_threshold
                FROM stock_items
                WHERE id = ?
                """,
                BigDecimal.class,
                stockItemId
            );

        Boolean trackExpiry =
            jdbcTemplate.queryForObject(
                """
                SELECT track_expiry
                FROM stock_items
                WHERE id = ?
                """,
                Boolean.class,
                stockItemId
            );

        assertThat(threshold)
            .isEqualByComparingTo("7.500");

        assertThat(trackExpiry)
            .isTrue();
    }

    @Test
    void productWithStockTrackingDisabledCannotBecomeStockItem()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "NO-STOCK-PRODUCT"
            );

        jdbcTemplate.update(
            """
            UPDATE products
            SET track_stock = FALSE
            WHERE id = ?
            """,
            productId
        );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/stock-items"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "productId": "%s",
                          "baseUnit": "PIECE",
                          "trackExpiry": false
                        }
                        """.formatted(
                            productId
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

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_items
                WHERE product_id = ?
                """,
                Long.class,
                productId
            );

        assertThat(count)
            .isZero();
    }

    @Test
    void ingredientWithStockTrackingDisabledCannotBecomeStockItem()
        throws Exception {

        UUID ingredientId =
            insertIngredient(
                organizationId,
                "NO-STOCK-INGREDIENT",
                "Non Stock Ingredient",
                "GRAM",
                true
            );

        jdbcTemplate.update(
            """
            UPDATE ingredients
            SET track_stock = FALSE
            WHERE id = ?
            """,
            ingredientId
        );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/stock-items"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "ingredientId": "%s",
                          "baseUnit": "GRAM",
                          "trackExpiry": false
                        }
                        """.formatted(
                            ingredientId
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

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM stock_items
                WHERE ingredient_id = ?
                """,
                Long.class,
                ingredientId
            );

        assertThat(count)
            .isZero();
    }
    // =========================================================
    // BALANCES
    // =========================================================

    @Test
    void balancesAreTenantScopedAndExposeAvailableQuantity()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "BALANCE-OWN"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE"
            );

        UUID campusId =
            insertCampus(
                organizationId,
                "BALANCE"
            );

        UUID locationId =
            insertLocation(
                campusId,
                "BALANCE",
                true
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                "Balance Storage"
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "10.000",
            "3.000"
        );

        UUID foreignOrganization =
            insertOrganization(
                "BALANCE-FOREIGN"
            );

        UUID foreignCategory =
            insertCategory(
                foreignOrganization,
                "Foreign Balance Category"
            );

        UUID foreignProduct =
            insertProduct(
                foreignOrganization,
                foreignCategory,
                "FOREIGN-BALANCE",
                "Foreign Balance Product"
            );

        UUID foreignStockItem =
            insertStockItemForProduct(
                foreignOrganization,
                foreignProduct,
                "PIECE"
            );

        UUID foreignCampus =
            insertCampus(
                foreignOrganization,
                "FBAL"
            );

        UUID foreignLocation =
            insertLocation(
                foreignCampus,
                "FBAL",
                true
            );

        UUID foreignStockLocation =
            insertStockLocation(
                foreignLocation,
                "Foreign Storage"
            );

        insertBalance(
            foreignStockItem,
            foreignStockLocation,
            "99.000",
            "0.000"
        );

        mockMvc.perform(
                get(
                    "/api/v1/admin/inventory/balances"
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
                    .value(1)
            )
            .andExpect(
                jsonPath("$[0].stockItemId")
                    .value(stockItemId.toString())
            )
            .andExpect(
                jsonPath("$[0].stockLocationId")
                    .value(stockLocationId.toString())
            )
            .andExpect(
                jsonPath("$[0].physicalQuantity")
                    .value(10.0)
            )
            .andExpect(
                jsonPath("$[0].reservedQuantity")
                    .value(3.0)
            )
            .andExpect(
                jsonPath("$[0].availableQuantity")
                    .value(7.0)
            );
    }

    // =========================================================
    // ATOMIC ADJUSTMENTS
    // =========================================================

    @Test
    void adjustmentCreatesBalanceAndMandatoryMovement()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "ADJUST-CREATE"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "ADJUST"
            );

        UUID idempotencyKey =
            UUID.randomUUID();

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        adjustmentBody(
                            stockItemId,
                            stockLocationId,
                            "10.000",
                            "PIECE",
                            idempotencyKey,
                            "Initial count"
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
                jsonPath("$.movement.movementType")
                    .value("ADJUSTMENT")
            )
            .andExpect(
                jsonPath("$.movement.referenceType")
                    .value("MANUAL_ADJUSTMENT")
            )
            .andExpect(
                jsonPath("$.movement.referenceId")
                    .value(idempotencyKey.toString())
            )
            .andExpect(
                jsonPath("$.balance.availableQuantity")
                    .value(10.0)
            );

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

        Long movements =
            movementCount(
                stockItemId,
                stockLocationId
            );

        assertThat(physical)
            .isEqualByComparingTo("10.000");

        assertThat(reserved)
            .isEqualByComparingTo("0.000");

        assertThat(movements)
            .isEqualTo(1L);
    }

    @Test
    void adjustmentCannotMakePhysicalStockNegative()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "ADJUST-NEGATIVE"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "NEGATIVE"
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "2.000",
            "0.000"
        );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        adjustmentBody(
                            stockItemId,
                            stockLocationId,
                            "-3.000",
                            "PIECE",
                            UUID.randomUUID(),
                            "Negative forbidden"
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
            "2.000",
            "0.000"
        );

        assertThat(
            movementCount(
                stockItemId,
                stockLocationId
            )
        ).isZero();
    }

    @Test
    void adjustmentCannotDropPhysicalBelowReserved()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "ADJUST-RESERVED"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "RESERVED"
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "10.000",
            "8.000"
        );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        adjustmentBody(
                            stockItemId,
                            stockLocationId,
                            "-3.000",
                            "PIECE",
                            UUID.randomUUID(),
                            "Reserved invariant"
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
            "10.000",
            "8.000"
        );

        assertThat(
            movementCount(
                stockItemId,
                stockLocationId
            )
        ).isZero();
    }

    @Test
    void adjustmentUnitMustMatchStockItemBaseUnit()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "ADJUST-UNIT"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "UNIT"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        adjustmentBody(
                            stockItemId,
                            stockLocationId,
                            "5.000",
                            "GRAM",
                            UUID.randomUUID(),
                            "Wrong unit"
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

        assertThat(
            movementCount(
                stockItemId,
                stockLocationId
            )
        ).isZero();
    }

    @Test
    void adjustmentReplayIsIdempotentAndDifferentPayloadConflicts()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "ADJUST-IDEMPOTENT"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "IDEMPOTENT"
            );

        UUID idempotencyKey =
            UUID.randomUUID();

        String original =
            adjustmentBody(
                stockItemId,
                stockLocationId,
                "5.000",
                "PIECE",
                idempotencyKey,
                "Idempotent adjustment"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(original)
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(original)
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.balance.physicalQuantity")
                    .value(5.0)
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        adjustmentBody(
                            stockItemId,
                            stockLocationId,
                            "6.000",
                            "PIECE",
                            idempotencyKey,
                            "Idempotent adjustment"
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

        assertThat(
            movementCount(
                stockItemId,
                stockLocationId
            )
        ).isEqualTo(1L);
    }

    @Test
    void adjustmentCannotUseForeignStockResources()
        throws Exception {

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-ADJUST"
            );

        UUID foreignCategory =
            insertCategory(
                foreignOrganization,
                "Foreign Adjustment Category"
            );

        UUID foreignProduct =
            insertProduct(
                foreignOrganization,
                foreignCategory,
                "FOREIGN-ADJUST",
                "Foreign Adjustment Product"
            );

        UUID foreignStockItem =
            insertStockItemForProduct(
                foreignOrganization,
                foreignProduct,
                "PIECE"
            );

        UUID foreignCampus =
            insertCampus(
                foreignOrganization,
                "FADJ"
            );

        UUID foreignLocation =
            insertLocation(
                foreignCampus,
                "FADJ",
                true
            );

        UUID foreignStockLocation =
            insertStockLocation(
                foreignLocation,
                "Foreign Adjustment Storage"
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        adjustmentBody(
                            foreignStockItem,
                            foreignStockLocation,
                            "1.000",
                            "PIECE",
                            UUID.randomUUID(),
                            "Foreign adjustment"
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

        assertThat(
            movementCount(
                foreignStockItem,
                foreignStockLocation
            )
        ).isZero();
    }

    @Test
    void adjustmentRejectsInactiveStockLocation()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "INACTIVE-LOCATION"
            );

        UUID stockItemId =
            insertStockItemForProduct(
                organizationId,
                productId,
                "PIECE"
            );

        UUID stockLocationId =
            insertOwnedStockLocation(
                "INACTIVE"
            );

        jdbcTemplate.update(
            """
            UPDATE stock_locations
            SET is_active = FALSE
            WHERE id = ?
            """,
            stockLocationId
        );

        mockMvc.perform(
                post(
                    "/api/v1/admin/inventory/adjustments"
                )
                    .header(
                        "Authorization",
                        bearer("product.write")
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        adjustmentBody(
                            stockItemId,
                            stockLocationId,
                            "1.000",
                            "PIECE",
                            UUID.randomUUID(),
                            "Inactive location"
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

        assertThat(
            movementCount(
                stockItemId,
                stockLocationId
            )
        ).isZero();
    }
    // =========================================================
    // HTTP / JWT
    // =========================================================

    private String adjustmentBody(
        UUID stockItemId,
        UUID stockLocationId,
        String physicalDelta,
        String unit,
        UUID idempotencyKey,
        String reason
    ) {

        return """
            {
              "stockItemId": "%s",
              "stockLocationId": "%s",
              "physicalDelta": %s,
              "unit": "%s",
              "idempotencyKey": "%s",
              "reason": "%s",
              "comment": "Inventory E2E"
            }
            """.formatted(
                stockItemId,
                stockLocationId,
                physicalDelta,
                unit,
                idempotencyKey,
                reason
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

    // =========================================================
    // DATABASE FIXTURES
    // =========================================================

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
        String prefix,
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
            VALUES (?, ?, ?, ?, 'STORAGE', ?)
            """,
            id,
            campusId,
            prefix + " Location",
            "LOC" + randomSuffix(),
            active
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
            skuPrefix + "-" + randomSuffix(),
            name
        );

        return id;
    }

    private UUID insertVariant(
        UUID productId,
        String name
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
            name,
            "VAR-" + randomSuffix()
        );

        return id;
    }

    private UUID insertIngredient(
        UUID tenantId,
        String codePrefix,
        String name,
        String unit,
        boolean active
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO ingredients (
                id,
                organization_id,
                code,
                name,
                base_unit,
                track_stock,
                is_active
            )
            VALUES (?, ?, ?, ?, ?, TRUE, ?)
            """,
            id,
            tenantId,
            codePrefix + "-" + randomSuffix(),
            name,
            unit,
            active
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
                prefix,
                true
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
            VALUES (?, ?, ?, 'STORAGE', TRUE)
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
        String baseUnit
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
            VALUES (?, ?, ?, ?, FALSE)
            """,
            id,
            tenantId,
            productId,
            baseUnit
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
            .replace("-", "")
            .substring(0, 10);
    }
}