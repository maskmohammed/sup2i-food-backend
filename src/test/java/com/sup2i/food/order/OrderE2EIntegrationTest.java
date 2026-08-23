package com.sup2i.food.order;

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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
class OrderE2EIntegrationTest {

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
    private UUID campusId;
    private UUID locationId;
    private UUID userId;
    private UUID studentId;
    private String email;
    private String sessionId;

    @BeforeEach
    void seedStudentTenantAndSession()
        throws Exception {

        String suffix =
            randomSuffix();

        organizationId =
            insertOrganization(
                "ORD-" + suffix
            );

        campusId =
            insertCampus(
                organizationId,
                "ORD-" + suffix,
                true
            );

        locationId =
            insertLocation(
                campusId,
                "ORD-" + suffix,
                true
            );

        userId =
            UUID.randomUUID();

        email =
            "orders-"
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
            VALUES (?, ?, ?, 'Orders', 'Student', 'ACTIVE')
            """,
            userId,
            organizationId,
            email
        );

        studentId =
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
            "STU-" + suffix
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "orders-e2e",
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
    // SECURITY / STUDENT CONTEXT
    // =========================================================

    @Test
    void anonymousOrderRequestIsRejected()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "ANON",
                "PACKAGED",
                "10.00",
                "20.00",
                false
            );

        mockMvc.perform(
                put(
                    "/api/v1/orders/{orderId}",
                    UUID.randomUUID()
                )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        orderBody(
                            locationId,
                            line(productId, null, 1)
                        )
                    )
            )
            .andExpect(
                status().isUnauthorized()
            );
    }

    @Test
    void suspendedStudentCannotCreateDraft()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "SUSPENDED",
                "PACKAGED",
                "10.00",
                "20.00",
                false
            );

        jdbcTemplate.update(
            """
            UPDATE students
            SET enrollment_status = 'SUSPENDED'
            WHERE id = ?
            """,
            studentId
        );

        mockMvc.perform(
                put(
                    "/api/v1/orders/{orderId}",
                    UUID.randomUUID()
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        orderBody(
                            locationId,
                            line(productId, null, 1)
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
    }

    // =========================================================
    // DRAFT / SUBMIT
    // =========================================================

    @Test
    void draftCreationSnapshotsPricingAndHistory()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "SNAPSHOT",
                "PACKAGED",
                "10.00",
                "20.00",
                false
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
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        orderBody(
                            locationId,
                            line(productId, null, 2)
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
                jsonPath("$.order.status")
                    .value("DRAFT")
            )
            .andExpect(
                jsonPath("$.order.locationId")
                    .value(locationId.toString())
            )
            .andExpect(
                jsonPath("$.order.studentId")
                    .value(studentId.toString())
            )
            .andExpect(
                jsonPath("$.order.currency")
                    .value("MAD")
            )
            .andExpect(
                jsonPath("$.order.items.length()")
                    .value(1)
            );

        assertThat(
            decimal(
                """
                SELECT unit_price
                FROM order_items
                WHERE order_id = ?
                """,
                orderId
            )
        ).isEqualByComparingTo("10.00");

        assertThat(
            integer(
                """
                SELECT quantity
                FROM order_items
                WHERE order_id = ?
                """,
                orderId
            )
        ).isEqualTo(2);

        assertThat(
            decimal(
                """
                SELECT tax_rate_snapshot
                FROM order_items
                WHERE order_id = ?
                """,
                orderId
            )
        ).isEqualByComparingTo("20.00");

        assertThat(
            decimal(
                """
                SELECT line_total
                FROM order_items
                WHERE order_id = ?
                """,
                orderId
            )
        ).isEqualByComparingTo("20.00");

        assertThat(
            string(
                """
                SELECT product_name_snapshot
                FROM order_items
                WHERE order_id = ?
                """,
                orderId
            )
        ).contains("SNAPSHOT");

        assertThat(
            longValue(
                """
                SELECT COUNT(*)
                FROM order_status_history
                WHERE order_id = ?
                  AND to_status = 'DRAFT'
                """,
                orderId
            )
        ).isEqualTo(1L);

        mockMvc.perform(
                get(
                    "/api/v1/orders/{orderId}",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("DRAFT")
            )
            .andExpect(
                jsonPath("$.items[0].quantity")
                    .value(2)
            );

        mockMvc.perform(
                get(
                    "/api/v1/orders/{orderId}/history",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$[0].toStatus")
                    .value("DRAFT")
            );
    }

    @Test
    void draftIsEditableSubmitIsIdempotentAndFreezesOrder()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "EDITABLE",
                "PACKAGED",
                "7.50",
                "10.00",
                false
            );

        UUID orderId =
            UUID.randomUUID();

        upsertDraft(
            orderId,
            line(productId, null, 1)
        );

        mockMvc.perform(
                put(
                    "/api/v1/orders/{orderId}",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        orderBody(
                            locationId,
                            line(productId, null, 3)
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.order.status")
                    .value("DRAFT")
            )
            .andExpect(
                jsonPath("$.order.items[0].quantity")
                    .value(3)
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/submit",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
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
                jsonPath("$.order.status")
                    .value("CREATED")
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/submit",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.order.status")
                    .value("CREATED")
            );

        mockMvc.perform(
                put(
                    "/api/v1/orders/{orderId}",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        orderBody(
                            locationId,
                            line(productId, null, 4)
                        )
                    )
            )
            .andExpect(
                status().isConflict()
            );
    }

    @Test
    void studentCannotExceedTwoActiveOrders()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "LIMIT",
                "PACKAGED",
                "4.00",
                "0.00",
                false
            );

        upsertDraft(
            UUID.randomUUID(),
            line(productId, null, 1)
        );

        upsertDraft(
            UUID.randomUUID(),
            line(productId, null, 1)
        );

        mockMvc.perform(
                put(
                    "/api/v1/orders/{orderId}",
                    UUID.randomUUID()
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        orderBody(
                            locationId,
                            line(productId, null, 1)
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
    }

    @Test
    void tenantBoundaryAndInactiveLocationAreRejected()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "LOCATION-GUARD",
                "PACKAGED",
                "5.00",
                "0.00",
                false
            );

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN"
            );

        UUID foreignCampus =
            insertCampus(
                foreignOrganization,
                "FOREIGN",
                true
            );

        UUID foreignLocation =
            insertLocation(
                foreignCampus,
                "FOREIGN",
                true
            );

        mockMvc.perform(
                put(
                    "/api/v1/orders/{orderId}",
                    UUID.randomUUID()
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        orderBody(
                            foreignLocation,
                            line(productId, null, 1)
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

        UUID inactiveLocation =
            insertLocation(
                campusId,
                "INACTIVE",
                false
            );

        mockMvc.perform(
                put(
                    "/api/v1/orders/{orderId}",
                    UUID.randomUUID()
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        orderBody(
                            inactiveLocation,
                            line(productId, null, 1)
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
    }

    @Test
    void submitRejectsPriceDrift()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "PRICE-DRIFT",
                "PACKAGED",
                "10.00",
                "20.00",
                false
            );

        UUID orderId =
            UUID.randomUUID();

        upsertDraft(
            orderId,
            line(productId, null, 1)
        );

        jdbcTemplate.update(
            """
            UPDATE products
            SET base_price = 11.00
            WHERE id = ?
            """,
            productId
        );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/submit",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
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
            string(
                """
                SELECT status
                FROM orders
                WHERE id = ?
                """,
                orderId
            )
        ).isEqualTo("DRAFT");
    }

    // =========================================================
    // RESERVATIONS
    // =========================================================

    @Test
    void packagedOrderReservesAvailableStockWithoutPhysicalConsumption()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "PACKAGED-STOCK",
                "PACKAGED",
                "10.00",
                "0.00",
                false
            );

        UUID stockItemId =
            insertProductStockItem(
                productId,
                "PIECE"
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                UUID.randomUUID(),
                "PACKAGED-STOCK",
                true
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "10.000",
            "4.000"
        );

        UUID orderId =
            createSubmittedOrder(
                line(productId, null, 6)
            );

        beginPayment(orderId)
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.order.status")
                    .value("AWAITING_PAYMENT")
            )
            .andExpect(
                jsonPath("$.order.reservations.length()")
                    .value(1)
            );

        assertBalance(
            stockItemId,
            stockLocationId,
            "10.000",
            "10.000"
        );

        assertThat(
            decimal(
                """
                SELECT quantity
                FROM stock_reservations
                WHERE order_id = ?
                """,
                orderId
            )
        ).isEqualByComparingTo("6.000");

        assertThat(
            string(
                """
                SELECT status
                FROM stock_reservations
                WHERE order_id = ?
                """,
                orderId
            )
        ).isEqualTo("ACTIVE");

        assertThat(
            movementCount(
                orderId,
                "RESERVATION"
            )
        ).isEqualTo(1L);

        assertThat(
            decimal(
                """
                SELECT im.physical_delta
                FROM inventory_movements im
                JOIN stock_reservations sr
                  ON sr.id = im.reference_id
                WHERE sr.order_id = ?
                  AND im.reference_type = 'STOCK_RESERVATION'
                  AND im.movement_type = 'RESERVATION'
                """,
                orderId
            )
        ).isEqualByComparingTo("0.000");

        assertThat(
            decimal(
                """
                SELECT im.reserved_delta
                FROM inventory_movements im
                JOIN stock_reservations sr
                  ON sr.id = im.reference_id
                WHERE sr.order_id = ?
                  AND im.reference_type = 'STOCK_RESERVATION'
                  AND im.movement_type = 'RESERVATION'
                """,
                orderId
            )
        ).isEqualByComparingTo("6.000");
    }

    @Test
    void variantStockItemOverridesProductStockItem()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "VARIANT-STOCK",
                "PACKAGED",
                "12.00",
                "0.00",
                false
            );

        UUID variantId =
            insertVariant(
                productId,
                "VARIANT-STOCK",
                "2.00"
            );

        UUID productStockItem =
            insertProductStockItem(
                productId,
                "PIECE"
            );

        UUID variantStockItem =
            insertVariantStockItem(
                variantId,
                "PIECE"
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                UUID.randomUUID(),
                "VARIANT-STOCK",
                true
            );

        insertBalance(
            productStockItem,
            stockLocationId,
            "10.000",
            "0.000"
        );

        insertBalance(
            variantStockItem,
            stockLocationId,
            "10.000",
            "0.000"
        );

        UUID orderId =
            createSubmittedOrder(
                line(productId, variantId, 4)
            );

        beginPayment(orderId)
            .andExpect(
                status().isOk()
            );

        assertThat(
            uuid(
                """
                SELECT stock_item_id
                FROM stock_reservations
                WHERE order_id = ?
                """,
                orderId
            )
        ).isEqualTo(variantStockItem);

        assertBalance(
            productStockItem,
            stockLocationId,
            "10.000",
            "0.000"
        );

        assertBalance(
            variantStockItem,
            stockLocationId,
            "10.000",
            "4.000"
        );
    }

    @Test
    void reservationSplitsAcrossStockLocationsDeterministically()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "SPLIT",
                "PACKAGED",
                "8.00",
                "0.00",
                false
            );

        UUID stockItemId =
            insertProductStockItem(
                productId,
                "PIECE"
            );

        UUID firstLocation =
            UUID.fromString(
                "00000000-0000-0000-0000-000000000101"
            );

        UUID secondLocation =
            UUID.fromString(
                "00000000-0000-0000-0000-000000000102"
            );

        insertStockLocation(
            locationId,
            firstLocation,
            "SPLIT-A",
            true
        );

        insertStockLocation(
            locationId,
            secondLocation,
            "SPLIT-B",
            true
        );

        insertBalance(
            stockItemId,
            firstLocation,
            "5.000",
            "2.000"
        );

        insertBalance(
            stockItemId,
            secondLocation,
            "10.000",
            "0.000"
        );

        UUID orderId =
            createSubmittedOrder(
                line(productId, null, 8)
            );

        beginPayment(orderId)
            .andExpect(
                status().isOk()
            );

        List<Allocation> allocations =
            jdbcTemplate.query(
                """
                SELECT stock_location_id, quantity
                FROM stock_reservations
                WHERE order_id = ?
                ORDER BY stock_location_id
                """,
                (resultSet, rowNum) ->
                    new Allocation(
                        resultSet.getObject(
                            "stock_location_id",
                            UUID.class
                        ),
                        resultSet.getBigDecimal(
                            "quantity"
                        )
                    ),
                orderId
            );

        assertThat(allocations)
            .hasSize(2);

        assertThat(
            allocations.get(0).stockLocationId()
        ).isEqualTo(firstLocation);

        assertThat(
            allocations.get(0).quantity()
        ).isEqualByComparingTo("3.000");

        assertThat(
            allocations.get(1).stockLocationId()
        ).isEqualTo(secondLocation);

        assertThat(
            allocations.get(1).quantity()
        ).isEqualByComparingTo("5.000");

        assertBalance(
            stockItemId,
            firstLocation,
            "5.000",
            "5.000"
        );

        assertBalance(
            stockItemId,
            secondLocation,
            "10.000",
            "5.000"
        );
    }

    @Test
    void insufficientMultiLineStockRollsBackEverything()
        throws Exception {

        UUID firstProduct =
            insertOwnedProduct(
                "ROLLBACK-A",
                "PACKAGED",
                "10.00",
                "0.00",
                false
            );

        UUID secondProduct =
            insertOwnedProduct(
                "ROLLBACK-B",
                "PACKAGED",
                "11.00",
                "0.00",
                false
            );

        UUID firstItem =
            insertProductStockItem(
                firstProduct,
                "PIECE"
            );

        UUID secondItem =
            insertProductStockItem(
                secondProduct,
                "PIECE"
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                UUID.randomUUID(),
                "ROLLBACK",
                true
            );

        insertBalance(
            firstItem,
            stockLocationId,
            "10.000",
            "0.000"
        );

        insertBalance(
            secondItem,
            stockLocationId,
            "2.000",
            "0.000"
        );

        UUID orderId =
            createSubmittedOrder(
                line(firstProduct, null, 3),
                line(secondProduct, null, 3)
            );

        beginPayment(orderId)
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );

        assertBalance(
            firstItem,
            stockLocationId,
            "10.000",
            "0.000"
        );

        assertBalance(
            secondItem,
            stockLocationId,
            "2.000",
            "0.000"
        );

        assertThat(
            longValue(
                """
                SELECT COUNT(*)
                FROM stock_reservations
                WHERE order_id = ?
                """,
                orderId
            )
        ).isZero();

        assertThat(
            movementCount(
                orderId,
                "RESERVATION"
            )
        ).isZero();

        assertThat(
            string(
                """
                SELECT status
                FROM orders
                WHERE id = ?
                """,
                orderId
            )
        ).isEqualTo("CREATED");
    }

    @Test
    void preparedOrderUsesWasteFactorAndProductRecipeFallback()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "PREPARED",
                "PREPARED",
                "30.00",
                "10.00",
                true
            );

        UUID variantId =
            insertVariant(
                productId,
                "PREPARED-VAR",
                "0.00"
            );

        UUID ingredientId =
            insertIngredient(
                "PREPARED-ING",
                "GRAM",
                true,
                true
            );

        UUID recipeId =
            insertRecipe(
                productId,
                null,
                1
            );

        insertRecipeItem(
            recipeId,
            ingredientId,
            "2.000",
            "GRAM",
            "0.1250"
        );

        UUID ingredientStockItem =
            insertIngredientStockItem(
                ingredientId,
                "GRAM"
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                UUID.randomUUID(),
                "PREPARED",
                true
            );

        insertBalance(
            ingredientStockItem,
            stockLocationId,
            "20.000",
            "1.000"
        );

        UUID orderId =
            createSubmittedOrder(
                line(productId, variantId, 2)
            );

        beginPayment(orderId)
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.order.status")
                    .value("AWAITING_PAYMENT")
            );

        assertThat(
            decimal(
                """
                SELECT quantity
                FROM stock_reservations
                WHERE order_id = ?
                """,
                orderId
            )
        ).isEqualByComparingTo("4.500");

        assertThat(
            uuid(
                """
                SELECT stock_item_id
                FROM stock_reservations
                WHERE order_id = ?
                """,
                orderId
            )
        ).isEqualTo(ingredientStockItem);

        assertBalance(
            ingredientStockItem,
            stockLocationId,
            "20.000",
            "5.500"
        );
    }

    @Test
    void beginPaymentReplayDoesNotDuplicateReservationsOrMovements()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "PAYMENT-REPLAY",
                "PACKAGED",
                "10.00",
                "0.00",
                false
            );

        UUID stockItemId =
            insertProductStockItem(
                productId,
                "PIECE"
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                UUID.randomUUID(),
                "PAYMENT-REPLAY",
                true
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "20.000",
            "0.000"
        );

        UUID orderId =
            createSubmittedOrder(
                line(productId, null, 4)
            );

        beginPayment(orderId)
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        beginPayment(orderId)
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        assertThat(
            longValue(
                """
                SELECT COUNT(*)
                FROM stock_reservations
                WHERE order_id = ?
                """,
                orderId
            )
        ).isEqualTo(1L);

        assertThat(
            movementCount(
                orderId,
                "RESERVATION"
            )
        ).isEqualTo(1L);

        assertBalance(
            stockItemId,
            stockLocationId,
            "20.000",
            "4.000"
        );

        assertThat(
            longValue(
                """
                SELECT COUNT(*)
                FROM order_status_history
                WHERE order_id = ?
                  AND to_status = 'AWAITING_PAYMENT'
                """,
                orderId
            )
        ).isEqualTo(1L);
    }

    @Test
    void cancelReleasesReservationsAndIsIdempotent()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "CANCEL",
                "PACKAGED",
                "10.00",
                "0.00",
                false
            );

        UUID stockItemId =
            insertProductStockItem(
                productId,
                "PIECE"
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                UUID.randomUUID(),
                "CANCEL",
                true
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "20.000",
            "2.000"
        );

        UUID orderId =
            createSubmittedOrder(
                line(productId, null, 5)
            );

        beginPayment(orderId)
            .andExpect(
                status().isOk()
            );

        assertBalance(
            stockItemId,
            stockLocationId,
            "20.000",
            "7.000"
        );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/cancel",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
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
                jsonPath("$.order.status")
                    .value("CANCELLED")
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/cancel",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
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
            "20.000",
            "2.000"
        );

        assertThat(
            string(
                """
                SELECT status
                FROM stock_reservations
                WHERE order_id = ?
                """,
                orderId
            )
        ).isEqualTo("RELEASED");

        assertThat(
            booleanValue(
                """
                SELECT released_at IS NOT NULL
                FROM stock_reservations
                WHERE order_id = ?
                """,
                orderId
            )
        ).isTrue();

        assertThat(
            movementCount(
                orderId,
                "RESERVATION_RELEASE"
            )
        ).isEqualTo(1L);

        assertThat(
            decimal(
                """
                SELECT im.reserved_delta
                FROM inventory_movements im
                JOIN stock_reservations sr
                  ON sr.id = im.reference_id
                WHERE sr.order_id = ?
                  AND im.reference_type = 'STOCK_RESERVATION'
                  AND im.movement_type = 'RESERVATION_RELEASE'
                """,
                orderId
            )
        ).isEqualByComparingTo("-5.000");
    }

    @Test
    void expiryRequiresElapsedTtlThenReleasesAndIsIdempotent()
        throws Exception {

        UUID productId =
            insertOwnedProduct(
                "EXPIRE",
                "PACKAGED",
                "10.00",
                "0.00",
                false
            );

        UUID stockItemId =
            insertProductStockItem(
                productId,
                "PIECE"
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                UUID.randomUUID(),
                "EXPIRE",
                true
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "20.000",
            "1.000"
        );

        UUID orderId =
            createSubmittedOrder(
                line(productId, null, 4)
            );

        beginPayment(orderId)
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/expire",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
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
            "20.000",
            "5.000"
        );

        jdbcTemplate.update(
            """
            UPDATE orders
            SET payment_expires_at =
                CURRENT_TIMESTAMP - INTERVAL '1 minute'
            WHERE id = ?
            """,
            orderId
        );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/expire",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
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
                jsonPath("$.order.status")
                    .value("EXPIRED")
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/expire",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
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
            "20.000",
            "1.000"
        );

        assertThat(
            string(
                """
                SELECT status
                FROM stock_reservations
                WHERE order_id = ?
                """,
                orderId
            )
        ).isEqualTo("EXPIRED");

        assertThat(
            booleanValue(
                """
                SELECT released_at IS NOT NULL
                FROM stock_reservations
                WHERE order_id = ?
                """,
                orderId
            )
        ).isTrue();

        assertThat(
            movementCount(
                orderId,
                "RESERVATION_RELEASE"
            )
        ).isEqualTo(1L);
    }

    // =========================================================
    // HTTP HELPERS
    // =========================================================

    private void upsertDraft(
        UUID orderId,
        Line... lines
    ) throws Exception {

        mockMvc.perform(
                put(
                    "/api/v1/orders/{orderId}",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        orderBody(
                            locationId,
                            lines
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.order.status")
                    .value("DRAFT")
            );
    }

    private UUID createSubmittedOrder(
        Line... lines
    ) throws Exception {

        UUID orderId =
            UUID.randomUUID();

        upsertDraft(
            orderId,
            lines
        );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/submit",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer()
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.order.status")
                    .value("CREATED")
            );

        return orderId;
    }

    private org.springframework.test.web.servlet.ResultActions
        beginPayment(
            UUID orderId
        ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/orders/{orderId}/begin-payment",
                orderId
            )
                .header(
                    "Authorization",
                    bearer()
                )
        );
    }

    private String orderBody(
        UUID businessLocationId,
        Line... lines
    ) {

        String items =
            Arrays.stream(lines)
                .map(this::lineJson)
                .collect(
                    Collectors.joining(",")
                );

        return """
            {
              "locationId": "%s",
              "currency": "MAD",
              "customerNote": "Orders A E2E",
              "items": [%s]
            }
            """.formatted(
                businessLocationId,
                items
            );
    }

    private String lineJson(
        Line line
    ) {

        String variant =
            line.variantId() == null
                ? "null"
                : "\""
                    + line.variantId()
                    + "\"";

        return """
            {
              "productId": "%s",
              "variantId": %s,
              "quantity": %d,
              "specialInstructions": "Orders A test"
            }
            """.formatted(
                line.productId(),
                variant,
                line.quantity()
            );
    }

    private Line line(
        UUID productId,
        UUID variantId,
        int quantity
    ) {

        return new Line(
            productId,
            variantId,
            quantity
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
    // TENANT / CATALOG FIXTURES
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
            "O" + randomSuffix()
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
        UUID parentCampusId,
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
            VALUES (?, ?, ?, ?, 'SNACK', ?)
            """,
            id,
            parentCampusId,
            prefix + " Location",
            "L" + randomSuffix(),
            active
        );

        return id;
    }

    private UUID insertOwnedProduct(
        String prefix,
        String type,
        String price,
        String taxRate,
        boolean prepared
    ) {

        UUID categoryId =
            insertCategory(
                organizationId,
                prefix
            );

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
                track_stock,
                is_prepared,
                is_active
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, TRUE)
            """,
            id,
            organizationId,
            categoryId,
            prefix + "-" + randomSuffix(),
            prefix + " Product",
            type,
            new BigDecimal(price),
            new BigDecimal(taxRate),
            prepared
        );

        return id;
    }

    private UUID insertCategory(
        UUID tenantId,
        String prefix
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
                is_active
            )
            VALUES (?, ?, ?, ?, TRUE)
            """,
            id,
            tenantId,
            prefix + " Category",
            "cat-" + randomSuffix()
        );

        return id;
    }

    private UUID insertVariant(
        UUID productId,
        String prefix,
        String priceDelta
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
            VALUES (?, ?, ?, ?, ?, TRUE, 0)
            """,
            id,
            productId,
            prefix + " Variant",
            prefix + "-V-" + randomSuffix(),
            new BigDecimal(priceDelta)
        );

        return id;
    }

    private UUID insertIngredient(
        String prefix,
        String unit,
        boolean active,
        boolean trackStock
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
                is_active,
                track_stock
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            organizationId,
            prefix + "-" + randomSuffix(),
            prefix + " Ingredient",
            unit,
            active,
            trackStock
        );

        return id;
    }

    private UUID insertRecipe(
        UUID productId,
        UUID variantId,
        int version
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO recipes (
                id,
                product_id,
                variant_id,
                version,
                is_active,
                effective_from
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                TRUE,
                CURRENT_TIMESTAMP - INTERVAL '1 minute'
            )
            """,
            id,
            productId,
            variantId,
            version
        );

        return id;
    }

    private void insertRecipeItem(
        UUID recipeId,
        UUID ingredientId,
        String quantity,
        String unit,
        String wasteFactor
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO recipe_items (
                id,
                recipe_id,
                ingredient_id,
                quantity,
                unit,
                waste_factor,
                is_critical
            )
            VALUES (?, ?, ?, ?, ?, ?, TRUE)
            """,
            UUID.randomUUID(),
            recipeId,
            ingredientId,
            new BigDecimal(quantity),
            unit,
            new BigDecimal(wasteFactor)
        );
    }

    // =========================================================
    // INVENTORY FIXTURES
    // =========================================================

    private UUID insertProductStockItem(
        UUID productId,
        String unit
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
            organizationId,
            productId,
            unit
        );

        return id;
    }

    private UUID insertVariantStockItem(
        UUID variantId,
        String unit
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_items (
                id,
                organization_id,
                variant_id,
                base_unit,
                track_expiry
            )
            VALUES (?, ?, ?, ?, FALSE)
            """,
            id,
            organizationId,
            variantId,
            unit
        );

        return id;
    }

    private UUID insertIngredientStockItem(
        UUID ingredientId,
        String unit
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_items (
                id,
                organization_id,
                ingredient_id,
                base_unit,
                track_expiry
            )
            VALUES (?, ?, ?, ?, FALSE)
            """,
            id,
            organizationId,
            ingredientId,
            unit
        );

        return id;
    }

    private UUID insertStockLocation(
        UUID businessLocationId,
        UUID stockLocationId,
        String prefix,
        boolean active
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO stock_locations (
                id,
                location_id,
                name,
                type,
                is_active
            )
            VALUES (?, ?, ?, 'COUNTER', ?)
            """,
            stockLocationId,
            businessLocationId,
            prefix + " Stock",
            active
        );

        return stockLocationId;
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
    // DATABASE ASSERTION HELPERS
    // =========================================================

    private void assertBalance(
        UUID stockItemId,
        UUID stockLocationId,
        String physical,
        String reserved
    ) {

        assertThat(
            decimal(
                """
                SELECT physical_quantity
                FROM stock_balances
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                """,
                stockItemId,
                stockLocationId
            )
        ).isEqualByComparingTo(physical);

        assertThat(
            decimal(
                """
                SELECT reserved_quantity
                FROM stock_balances
                WHERE stock_item_id = ?
                  AND stock_location_id = ?
                """,
                stockItemId,
                stockLocationId
            )
        ).isEqualByComparingTo(reserved);
    }

    private long movementCount(
        UUID orderId,
        String movementType
    ) {

        return longValue(
            """
            SELECT COUNT(*)
            FROM inventory_movements im
            JOIN stock_reservations sr
              ON sr.id = im.reference_id
            WHERE sr.order_id = ?
              AND im.reference_type = 'STOCK_RESERVATION'
              AND im.movement_type = ?
            """,
            orderId,
            movementType
        );
    }

    private BigDecimal decimal(
        String sql,
        Object... args
    ) {

        return jdbcTemplate.queryForObject(
            sql,
            BigDecimal.class,
            args
        );
    }

    private Integer integer(
        String sql,
        Object... args
    ) {

        return jdbcTemplate.queryForObject(
            sql,
            Integer.class,
            args
        );
    }

    private Long longValue(
        String sql,
        Object... args
    ) {

        return jdbcTemplate.queryForObject(
            sql,
            Long.class,
            args
        );
    }

    private String string(
        String sql,
        Object... args
    ) {

        return jdbcTemplate.queryForObject(
            sql,
            String.class,
            args
        );
    }

    private UUID uuid(
        String sql,
        Object... args
    ) {

        return jdbcTemplate.queryForObject(
            sql,
            UUID.class,
            args
        );
    }

    private Boolean booleanValue(
        String sql,
        Object... args
    ) {

        return jdbcTemplate.queryForObject(
            sql,
            Boolean.class,
            args
        );
    }

    private String randomSuffix() {

        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10);
    }

    private record Line(
        UUID productId,
        UUID variantId,
        int quantity
    ) {
    }

    private record Allocation(
        UUID stockLocationId,
        BigDecimal quantity
    ) {
    }
}
