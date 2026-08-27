package com.sup2i.food.order;

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
import java.time.OffsetDateTime;
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

    private UUID organizationId;
    private UUID campusId;
    private UUID locationId;

    private Actor actor;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "ORD"
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

        actor =
            insertActor(
                organizationId,
                campusId,
                true,
                "ACTIVE",
                "MAIN"
            );
    }

    // =========================================================
    // 01 - SECURITY
    // =========================================================

    @Test
    void unauthenticatedRequestsAreRejected()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PACKAGED",
                "AUTH",
                "10.00"
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
                        draftBody(
                            locationId,
                            productId,
                            null,
                            1,
                            "Unauthenticated"
                        )
                    )
            )
            .andExpect(
                status().isUnauthorized()
            );
    }

    // =========================================================
    // 02 - STUDENT GUARDS
    // =========================================================

    @Test
    void nonStudentAndSuspendedStudentAreRejected()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PACKAGED",
                "STUDENT",
                "10.00"
            );

        Actor nonStudent =
            insertActor(
                organizationId,
                campusId,
                false,
                null,
                "NONSTUDENT"
            );

        putDraft(
            UUID.randomUUID(),
            nonStudent,
            draftBody(
                locationId,
                productId,
                null,
                1,
                "No student profile"
            )
        )
            .andExpect(
                status().isConflict()
            );

        Actor suspended =
            insertActor(
                organizationId,
                campusId,
                true,
                "SUSPENDED",
                "SUSPENDED"
            );

        putDraft(
            UUID.randomUUID(),
            suspended,
            draftBody(
                locationId,
                productId,
                null,
                1,
                "Suspended student"
            )
        )
            .andExpect(
                status().isConflict()
            );
    }

    // =========================================================
    // 03 - DRAFT SNAPSHOT / IDEMPOTENCY
    // =========================================================

    @Test
    void draftCreationSnapshotsPriceAndReplaysIdempotently()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PACKAGED",
                "SNAP",
                "10.00"
            );

        UUID variantId =
            insertVariant(
                productId,
                "LARGE",
                "2.50"
            );

        UUID orderId =
            UUID.randomUUID();

        String body =
            draftBody(
                locationId,
                productId,
                variantId,
                2,
                "Snapshot test"
            );

        putDraft(
            orderId,
            actor,
            body
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.order.id")
                    .value(
                        orderId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.order.status")
                    .value("DRAFT")
            )
            .andExpect(
                jsonPath("$.order.currency")
                    .value("MAD")
            )
            .andExpect(
                jsonPath("$.order.customerNote")
                    .value("Snapshot test")
            )
            .andExpect(
                jsonPath("$.order.items.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.order.items[0].productId")
                    .value(
                        productId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.order.items[0].variantId")
                    .value(
                        variantId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.order.items[0].unitPrice")
                    .value(12.50)
            )
            .andExpect(
                jsonPath("$.order.items[0].quantity")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.order.items[0].lineTotal")
                    .value(25.00)
            )
            .andExpect(
                jsonPath("$.order.subtotal")
                    .value(25.00)
            )
            .andExpect(
                jsonPath("$.order.total")
                    .value(25.00)
            );

        putDraft(
            orderId,
            actor,
            body
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        assertThat(
            orderCount(orderId)
        ).isEqualTo(1L);

        assertThat(
            orderItemCount(orderId)
        ).isEqualTo(1L);

        assertThat(
            historyCount(orderId)
        ).isEqualTo(1L);
    }

    // =========================================================
    // 04 - DRAFT EDIT / FREEZE
    // =========================================================

    @Test
    void draftCanBeEditedAndIsFrozenAfterSubmit()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PACKAGED",
                "EDIT",
                "10.00"
            );

        UUID orderId =
            UUID.randomUUID();

        putDraft(
            orderId,
            actor,
            draftBody(
                locationId,
                productId,
                null,
                1,
                "Initial"
            )
        )
            .andExpect(
                status().isOk()
            );

        putDraft(
            orderId,
            actor,
            draftBody(
                locationId,
                productId,
                null,
                3,
                "Updated"
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
                jsonPath("$.order.items[0].quantity")
                    .value(3)
            )
            .andExpect(
                jsonPath("$.order.customerNote")
                    .value("Updated")
            )
            .andExpect(
                jsonPath("$.order.total")
                    .value(30.00)
            );

        submit(
            orderId,
            actor
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

        submit(
            orderId,
            actor
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        putDraft(
            orderId,
            actor,
            draftBody(
                locationId,
                productId,
                null,
                4,
                "Must fail"
            )
        )
            .andExpect(
                status().isConflict()
            );
    }

    // =========================================================
    // 05 - MAX ACTIVE ORDERS
    // =========================================================

    @Test
    void maximumTwoActiveOrdersIsEnforced()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PACKAGED",
                "LIMIT",
                "10.00"
            );

        String body =
            draftBody(
                locationId,
                productId,
                null,
                1,
                "Active limit"
            );

        putDraft(
            UUID.randomUUID(),
            actor,
            body
        )
            .andExpect(
                status().isOk()
            );

        putDraft(
            UUID.randomUUID(),
            actor,
            body
        )
            .andExpect(
                status().isOk()
            );

        putDraft(
            UUID.randomUUID(),
            actor,
            body
        )
            .andExpect(
                status().isConflict()
            );
    }

    // =========================================================
    // 06 - LOCATION / TENANT GUARDS
    // =========================================================

    @Test
    void tenantAndLocationIsolationAreEnforced()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PACKAGED",
                "SCOPE",
                "10.00"
            );

        UUID foreignOrg =
            insertOrganization(
                "FOREIGN"
            );

        UUID foreignCampus =
            insertCampus(
                foreignOrg,
                "FOREIGN",
                true
            );

        UUID foreignLocation =
            insertLocation(
                foreignCampus,
                "FOREIGN",
                "SNACK",
                true
            );

        putDraft(
            UUID.randomUUID(),
            actor,
            draftBody(
                foreignLocation,
                productId,
                null,
                1,
                "Foreign location"
            )
        )
            .andExpect(
                status().isBadRequest()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "VALIDATION_ERROR"
                    )
            );

        UUID inactiveLocation =
            insertLocation(
                campusId,
                "INACTIVE",
                "SNACK",
                false
            );

        putDraft(
            UUID.randomUUID(),
            actor,
            draftBody(
                inactiveLocation,
                productId,
                null,
                1,
                "Inactive location"
            )
        )
            .andExpect(
                status().isConflict()
            );
    }

    // =========================================================
    // 07 - PACKAGED SPLIT ALLOCATION
    // =========================================================

    @Test
    void packagedReservationSplitsAcrossStockLocations()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PACKAGED",
                "SPLIT",
                "10.00"
            );

        UUID stockItemId =
            insertProductStockItem(
                organizationId,
                productId,
                "PIECE"
            );

        UUID stockLocationA =
            insertStockLocation(
                locationId,
                "SPLIT-A",
                true
            );

        UUID stockLocationB =
            insertStockLocation(
                locationId,
                "SPLIT-B",
                true
            );

        insertBalance(
            stockItemId,
            stockLocationA,
            "3.000",
            "0.000"
        );

        insertBalance(
            stockItemId,
            stockLocationB,
            "7.000",
            "0.000"
        );

        UUID otherLocation =
            insertLocation(
                campusId,
                "OTHER-SNACK",
                "SNACK",
                true
            );

        UUID outsideStockLocation =
            insertStockLocation(
                otherLocation,
                "OUTSIDE",
                true
            );

        insertBalance(
            stockItemId,
            outsideStockLocation,
            "100.000",
            "0.000"
        );

        UUID orderId =
            createSubmittedOrder(
                productId,
                null,
                8
            );

        beginPayment(
            orderId,
            actor
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
                    .value(
                        "AWAITING_PAYMENT"
                    )
            );

        assertThat(
            reservationCount(
                orderId
            )
        ).isEqualTo(2L);

        assertThat(
            reservationQuantity(
                orderId
            )
        ).isEqualByComparingTo(
            "8.000"
        );

        assertThat(
            reservedQuantity(
                stockItemId,
                stockLocationA
            ).add(
                reservedQuantity(
                    stockItemId,
                    stockLocationB
                )
            )
        ).isEqualByComparingTo(
            "8.000"
        );

        assertThat(
            reservedQuantity(
                stockItemId,
                outsideStockLocation
            )
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            movementCount(
                orderId,
                "RESERVATION"
            )
        ).isEqualTo(2L);
    }

    // =========================================================
    // 08 - VARIANT STOCK
    // =========================================================

    @Test
    void selectedVariantUsesVariantStockItem()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PACKAGED",
                "VARIANT",
                "10.00"
            );

        UUID variantId =
            insertVariant(
                productId,
                "VARIANT",
                "1.00"
            );

        UUID productStockItem =
            insertProductStockItem(
                organizationId,
                productId,
                "PIECE"
            );

        UUID variantStockItem =
            insertVariantStockItem(
                organizationId,
                variantId,
                "PIECE"
            );

        UUID stockLocation =
            insertStockLocation(
                locationId,
                "VARIANT",
                true
            );

        insertBalance(
            productStockItem,
            stockLocation,
            "20.000",
            "0.000"
        );

        insertBalance(
            variantStockItem,
            stockLocation,
            "5.000",
            "0.000"
        );

        UUID orderId =
            createSubmittedOrder(
                productId,
                variantId,
                3
            );

        beginPayment(
            orderId,
            actor
        )
            .andExpect(
                status().isOk()
            );

        assertThat(
            reservationStockItem(
                orderId
            )
        ).isEqualTo(
            variantStockItem
        );

        assertThat(
            reservedQuantity(
                variantStockItem,
                stockLocation
            )
        ).isEqualByComparingTo(
            "3.000"
        );

        assertThat(
            reservedQuantity(
                productStockItem,
                stockLocation
            )
        ).isEqualByComparingTo(
            "0.000"
        );
    }

    // =========================================================
    // 09 - PREPARED RECIPE / WASTE / VARIANT FALLBACK
    // =========================================================

    @Test
    void preparedReservationUsesRecipeWasteAndVariantFallback()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PREPARED",
                "RECIPE",
                "15.00"
            );

        UUID variantId =
            insertVariant(
                productId,
                "RECIPE-VARIANT",
                "2.00"
            );

        UUID ingredientId =
            insertIngredient(
                organizationId,
                "FLOUR",
                "GRAM"
            );

        insertRecipe(
            productId,
            null,
            ingredientId,
            "2.000",
            "GRAM",
            "0.2500"
        );

        UUID ingredientStockItem =
            insertIngredientStockItem(
                organizationId,
                ingredientId,
                "GRAM"
            );

        UUID stockLocation =
            insertStockLocation(
                locationId,
                "KITCHEN",
                true
            );

        insertBalance(
            ingredientStockItem,
            stockLocation,
            "10.000",
            "0.000"
        );

        UUID orderId =
            createSubmittedOrder(
                productId,
                variantId,
                2
            );

        beginPayment(
            orderId,
            actor
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.order.status")
                    .value(
                        "AWAITING_PAYMENT"
                    )
            );

        assertThat(
            reservationCount(
                orderId
            )
        ).isEqualTo(1L);

        assertThat(
            reservationStockItem(
                orderId
            )
        ).isEqualTo(
            ingredientStockItem
        );

        assertThat(
            reservationQuantity(
                orderId
            )
        ).isEqualByComparingTo(
            "5.000"
        );

        assertThat(
            reservedQuantity(
                ingredientStockItem,
                stockLocation
            )
        ).isEqualByComparingTo(
            "5.000"
        );
    }

    // =========================================================
    // 10 - ATOMIC ROLLBACK
    // =========================================================

    @Test
    void insufficientStockRollsBackWholeReservation()
        throws Exception {

        UUID productA =
            insertProduct(
                organizationId,
                "PACKAGED",
                "ROLL-A",
                "10.00"
            );

        UUID productB =
            insertProduct(
                organizationId,
                "PACKAGED",
                "ROLL-B",
                "10.00"
            );

        UUID itemA =
            insertProductStockItem(
                organizationId,
                productA,
                "PIECE"
            );

        UUID itemB =
            insertProductStockItem(
                organizationId,
                productB,
                "PIECE"
            );

        UUID stockLocation =
            insertStockLocation(
                locationId,
                "ROLLBACK",
                true
            );

        insertBalance(
            itemA,
            stockLocation,
            "10.000",
            "0.000"
        );

        insertBalance(
            itemB,
            stockLocation,
            "1.000",
            "0.000"
        );

        UUID orderId =
            UUID.randomUUID();

        putDraft(
            orderId,
            actor,
            twoItemDraftBody(
                locationId,
                productA,
                2,
                productB,
                2
            )
        )
            .andExpect(
                status().isOk()
            );

        submit(
            orderId,
            actor
        )
            .andExpect(
                status().isOk()
            );

        beginPayment(
            orderId,
            actor
        )
            .andExpect(
                status().isConflict()
            );

        assertThat(
            reservedQuantity(
                itemA,
                stockLocation
            )
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            reservedQuantity(
                itemB,
                stockLocation
            )
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            reservationCount(
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
            orderStatus(
                orderId
            )
        ).isEqualTo(
            "CREATED"
        );
    }

    // =========================================================
    // 11 - BEGIN PAYMENT REPLAY
    // =========================================================

    @Test
    void beginPaymentReplayDoesNotDoubleReserve()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PACKAGED",
                "PAYREPLAY",
                "10.00"
            );

        UUID stockItemId =
            insertProductStockItem(
                organizationId,
                productId,
                "PIECE"
            );

        UUID stockLocation =
            insertStockLocation(
                locationId,
                "PAYREPLAY",
                true
            );

        insertBalance(
            stockItemId,
            stockLocation,
            "10.000",
            "0.000"
        );

        UUID orderId =
            createSubmittedOrder(
                productId,
                null,
                3
            );

        beginPayment(
            orderId,
            actor
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        assertThat(
            reservationCount(
                orderId
            )
        ).isEqualTo(1L);

        assertThat(
            reservedQuantity(
                stockItemId,
                stockLocation
            )
        ).isEqualByComparingTo(
            "3.000"
        );

        assertThat(
            movementCount(
                orderId,
                "RESERVATION"
            )
        ).isEqualTo(1L);

        beginPayment(
            orderId,
            actor
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        assertThat(
            reservationCount(
                orderId
            )
        ).isEqualTo(1L);

        assertThat(
            reservedQuantity(
                stockItemId,
                stockLocation
            )
        ).isEqualByComparingTo(
            "3.000"
        );

        assertThat(
            movementCount(
                orderId,
                "RESERVATION"
            )
        ).isEqualTo(1L);
    }

    // =========================================================
    // 12 - CANCELLATION / RELEASE
    // =========================================================

    @Test
    void cancelReleasesReservationsAndReplays()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "CANCEL",
                3
            );

        cancel(
            fixture.orderId(),
            actor
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

        assertThat(
            reservedQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            )
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            reservationStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "RELEASED"
        );

        assertThat(
            movementCount(
                fixture.orderId(),
                "RESERVATION_RELEASE"
            )
        ).isEqualTo(1L);

        cancel(
            fixture.orderId(),
            actor
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        assertThat(
            movementCount(
                fixture.orderId(),
                "RESERVATION_RELEASE"
            )
        ).isEqualTo(1L);
    }

    // =========================================================
    // 13 - EXPIRATION
    // =========================================================

    @Test
    void expireHonorsDeadlineReleasesAndReplays()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "EXPIRE",
                3
            );

        expire(
            fixture.orderId(),
            actor
        )
            .andExpect(
                status().isConflict()
            );

        jdbcTemplate.update(
            """
            UPDATE orders
            SET payment_expires_at =
                CURRENT_TIMESTAMP
                - INTERVAL '1 minute'
            WHERE id = ?
            """,
            fixture.orderId()
        );

        expire(
            fixture.orderId(),
            actor
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

        assertThat(
            reservedQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            )
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            reservationStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "EXPIRED"
        );

        assertThat(
            movementCount(
                fixture.orderId(),
                "RESERVATION_RELEASE"
            )
        ).isEqualTo(1L);

        expire(
            fixture.orderId(),
            actor
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        assertThat(
            movementCount(
                fixture.orderId(),
                "RESERVATION_RELEASE"
            )
        ).isEqualTo(1L);
    }

    // =========================================================
    // 14 - PAYMENT
    // =========================================================

    @Test
    void payTransitionsAwaitingPaymentToPaidAndIsIdempotent()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "PAY",
                2
            );

        pay(
            fixture.orderId(),
            actor
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
                    .value("QUEUED")
            );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "QUEUED"
        );

        assertThat(
            orderPaymentStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "COMPLETED"
        );

        assertThat(
            orderPaidAt(
                fixture.orderId()
            )
        ).isNotNull();

        assertThat(
            historyCount(
                fixture.orderId()
            )
        ).isEqualTo(5L);

        assertThat(
            paymentCount(
                fixture.orderId()
            )
        ).isEqualTo(1L);

        pay(
            fixture.orderId(),
            actor
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
                fixture.orderId()
            )
        ).isEqualTo(5L);

        assertThat(
            paymentCount(
                fixture.orderId()
            )
        ).isEqualTo(1L);
    }

    @Test
    void payCreatesLinkedPaymentRecordMatchingOrderTotal()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "PAYRECORD",
                3
            );

        pay(
            fixture.orderId(),
            actor
        )
            .andExpect(
                status().isOk()
            );

        assertThat(
            paymentCount(
                fixture.orderId()
            )
        ).isEqualTo(1L);

        assertThat(
            paymentAmount(
                fixture.orderId()
            )
        ).isEqualByComparingTo(
            "30.00"
        );

        assertThat(
            paymentStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "COMPLETED"
        );

        assertThat(
            paymentMethod(
                fixture.orderId()
            )
        ).isEqualTo(
            "ONLINE"
        );
    }

    @Test
    void payDoesNotConsumeOrReleaseStockReservation()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "PAYSTOCK",
                2
            );

        BigDecimal reservedBefore =
            reservedQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            );

        pay(
            fixture.orderId(),
            actor
        )
            .andExpect(
                status().isOk()
            );

        assertThat(
            reservationStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "ACTIVE"
        );

        assertThat(
            reservedQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            )
        ).isEqualByComparingTo(
            reservedBefore
        );

        assertThat(
            movementCount(
                fixture.orderId(),
                "RESERVATION_RELEASE"
            )
        ).isZero();
    }

    @Test
    void payRejectsWrongStatus()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PACKAGED",
                "PAYWRONG",
                "10.00"
            );

        UUID orderId =
            createSubmittedOrder(
                productId,
                null,
                1
            );

        pay(
            orderId,
            actor
        )
            .andExpect(
                status().isConflict()
            );

        assertThat(
            orderStatus(
                orderId
            )
        ).isEqualTo(
            "CREATED"
        );

        assertThat(
            historyCount(
                orderId
            )
        ).isEqualTo(2L);
    }

    @Test
    void payRejectsCrossTenantAccess()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "PAYSCOPE",
                1
            );

        Actor otherStudent =
            insertActor(
                organizationId,
                campusId,
                true,
                "ACTIVE",
                "PAYOTHER"
            );

        pay(
            fixture.orderId(),
            otherStudent
        )
            .andExpect(
                status().isNotFound()
            );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "AWAITING_PAYMENT"
        );
    }

    @Test
    void payRejectsUnauthenticatedRequest()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "PAYAUTH",
                1
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/pay",
                    fixture.orderId()
                )
            )
            .andExpect(
                status().isUnauthorized()
            );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "AWAITING_PAYMENT"
        );
    }

    // =========================================================
    // 15 - HISTORY / OWNERSHIP
    // =========================================================

    @Test
    void historyCapturesLifecycleAndOwnershipIsScoped()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "HISTORY",
                2
            );

        cancel(
            fixture.orderId(),
            actor
        )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                get(
                    "/api/v1/orders/{orderId}/history",
                    fixture.orderId()
                )
                    .header(
                        "Authorization",
                        bearer(actor)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(4)
            )
            .andExpect(
                jsonPath("$[0].toStatus")
                    .value("DRAFT")
            )
            .andExpect(
                jsonPath("$[1].toStatus")
                    .value("CREATED")
            )
            .andExpect(
                jsonPath("$[2].toStatus")
                    .value(
                        "AWAITING_PAYMENT"
                    )
            )
            .andExpect(
                jsonPath("$[3].toStatus")
                    .value("CANCELLED")
            )
            .andExpect(
                jsonPath("$[0].source")
                    .value("MOBILE")
            )
            .andExpect(
                jsonPath("$[1].source")
                    .value("MOBILE")
            )
            .andExpect(
                jsonPath("$[2].source")
                    .value("MOBILE")
            )
            .andExpect(
                jsonPath("$[3].source")
                    .value("MOBILE")
            );

        Actor otherStudent =
            insertActor(
                organizationId,
                campusId,
                true,
                "ACTIVE",
                "OTHER"
            );

        mockMvc.perform(
                get(
                    "/api/v1/orders/{orderId}",
                    fixture.orderId()
                )
                    .header(
                        "Authorization",
                        bearer(otherStudent)
                    )
            )
            .andExpect(
                status().isNotFound()
            );

        mockMvc.perform(
                get(
                    "/api/v1/orders/{orderId}/history",
                    fixture.orderId()
                )
                    .header(
                        "Authorization",
                        bearer(otherStudent)
                    )
            )
            .andExpect(
                status().isNotFound()
            );
    }

    // =========================================================
    // HTTP HELPERS
    // =========================================================

    private ResultActions putDraft(
        UUID orderId,
        Actor requestActor,
        String body
    ) throws Exception {

        return mockMvc.perform(
            put(
                "/api/v1/orders/{orderId}",
                orderId
            )
                .header(
                    "Authorization",
                    bearer(requestActor)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(body)
        );
    }

    private ResultActions submit(
        UUID orderId,
        Actor requestActor
    ) throws Exception {

        return transition(
            orderId,
            requestActor,
            "submit"
        );
    }

    private ResultActions beginPayment(
        UUID orderId,
        Actor requestActor
    ) throws Exception {

        return transition(
            orderId,
            requestActor,
            "begin-payment"
        );
    }

    private ResultActions cancel(
        UUID orderId,
        Actor requestActor
    ) throws Exception {

        return transition(
            orderId,
            requestActor,
            "cancel"
        );
    }

    private ResultActions expire(
        UUID orderId,
        Actor requestActor
    ) throws Exception {

        return transition(
            orderId,
            requestActor,
            "expire"
        );
    }

    private ResultActions pay(
        UUID orderId,
        Actor requestActor
    ) throws Exception {

        return transition(
            orderId,
            requestActor,
            "pay"
        );
    }

    private ResultActions transition(
        UUID orderId,
        Actor requestActor,
        String transition
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/orders/{orderId}/{transition}",
                orderId,
                transition
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
    // ORDER HELPERS
    // =========================================================

    private UUID createSubmittedOrder(
        UUID productId,
        UUID variantId,
        int quantity
    ) throws Exception {

        UUID orderId =
            UUID.randomUUID();

        putDraft(
            orderId,
            actor,
            draftBody(
                locationId,
                productId,
                variantId,
                quantity,
                "Orders A E2E"
            )
        )
            .andExpect(
                status().isOk()
            );

        submit(
            orderId,
            actor
        )
            .andExpect(
                status().isOk()
            );

        return orderId;
    }

    private ReservationFixture awaitingPackagedOrder(
        String prefix,
        int quantity
    ) throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PACKAGED",
                prefix,
                "10.00"
            );

        UUID stockItemId =
            insertProductStockItem(
                organizationId,
                productId,
                "PIECE"
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                prefix,
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
                productId,
                null,
                quantity
            );

        beginPayment(
            orderId,
            actor
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.order.status")
                    .value(
                        "AWAITING_PAYMENT"
                    )
            );

        return new ReservationFixture(
            orderId,
            stockItemId,
            stockLocationId
        );
    }

    // =========================================================
    // JSON
    // =========================================================

    private String draftBody(
        UUID selectedLocationId,
        UUID productId,
        UUID variantId,
        int quantity,
        String note
    ) {

        String variant =
            variantId == null
                ? ""
                : """
                  "variantId": "%s",
                  """.formatted(
                    variantId
                );

        return """
            {
              "locationId": "%s",
              "currency": "MAD",
              "customerNote": "%s",
              "items": [
                {
                  "productId": "%s",
                  %s
                  "quantity": %d,
                  "specialInstructions": "E2E"
                }
              ]
            }
            """.formatted(
                selectedLocationId,
                note,
                productId,
                variant,
                quantity
            );
    }

    private String twoItemDraftBody(
        UUID selectedLocationId,
        UUID productA,
        int quantityA,
        UUID productB,
        int quantityB
    ) {

        return """
            {
              "locationId": "%s",
              "currency": "MAD",
              "customerNote": "Atomic reservation",
              "items": [
                {
                  "productId": "%s",
                  "quantity": %d
                },
                {
                  "productId": "%s",
                  "quantity": %d
                }
              ]
            }
            """.formatted(
                selectedLocationId,
                productA,
                quantityA,
                productB,
                quantityB
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
        boolean student,
        String enrollmentStatus,
        String prefix
    ) {

        UUID userId =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

        String email =
            "orders-"
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
            "Orders",
            prefix
        );

        UUID studentId =
            null;

        if (student) {

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
                VALUES (?, ?, ?, ?, ?)
                """,
                studentId,
                userId,
                selectedCampusId,
                "STU-" + randomSuffix(),
                enrollmentStatus
            );
        }

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "orders-a-e2e-"
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
    // CATALOG FIXTURES
    // =========================================================

    private UUID insertProduct(
        UUID tenantId,
        String productType,
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

        boolean prepared =
            "PREPARED".equals(
                productType
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
                ?, ?, ?, ?, ?, ?,
                ?, 0.00, 0,
                TRUE, ?, TRUE
            )
            """,
            productId,
            tenantId,
            categoryId,
            prefix + "-" + randomSuffix(),
            prefix + " Product",
            productType,
            new BigDecimal(price),
            prepared
        );

        return productId;
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
            "VAR-" + randomSuffix(),
            new BigDecimal(
                priceDelta
            )
        );

        return id;
    }

    private UUID insertIngredient(
        UUID tenantId,
        String prefix,
        String unit
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
                is_active
            )
            VALUES (?, ?, ?, ?, ?, TRUE)
            """,
            id,
            tenantId,
            prefix + "-" + randomSuffix(),
            prefix + " Ingredient",
            unit
        );

        return id;
    }

    private void insertRecipe(
        UUID productId,
        UUID variantId,
        UUID ingredientId,
        String quantity,
        String unit,
        String wasteFactor
    ) {

        UUID recipeId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO recipes (
                id,
                product_id,
                variant_id,
                version,
                is_active,
                effective_from,
                effective_to
            )
            VALUES (
                ?, ?, ?, 1, TRUE,
                CURRENT_TIMESTAMP,
                NULL
            )
            """,
            recipeId,
            productId,
            variantId
        );

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
            new BigDecimal(
                quantity
            ),
            unit,
            wasteFactor == null
                ? null
                : new BigDecimal(
                    wasteFactor
                )
        );
    }

    // =========================================================
    // INVENTORY FIXTURES
    // =========================================================

    private UUID insertStockLocation(
        UUID selectedLocationId,
        String prefix,
        boolean active
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
            VALUES (?, ?, ?, 'STORAGE', ?)
            """,
            id,
            selectedLocationId,
            prefix + " Stock",
            active
        );

        return id;
    }

    private UUID insertProductStockItem(
        UUID tenantId,
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
            tenantId,
            productId,
            unit
        );

        return id;
    }

    private UUID insertVariantStockItem(
        UUID tenantId,
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
            tenantId,
            variantId,
            unit
        );

        return id;
    }

    private UUID insertIngredientStockItem(
        UUID tenantId,
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
            tenantId,
            ingredientId,
            unit
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

    // =========================================================
    // DATABASE ASSERTION HELPERS
    // =========================================================

    private Long orderCount(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM orders
            WHERE id = ?
            """,
            Long.class,
            orderId
        );
    }

    private Long orderItemCount(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM order_items
            WHERE order_id = ?
            """,
            Long.class,
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

    private String orderPaymentStatus(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT payment_status
            FROM orders
            WHERE id = ?
            """,
            String.class,
            orderId
        );
    }

    private OffsetDateTime orderPaidAt(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT paid_at
            FROM orders
            WHERE id = ?
            """,
            OffsetDateTime.class,
            orderId
        );
    }

    private Long paymentCount(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM payments
            WHERE order_id = ?
            """,
            Long.class,
            orderId
        );
    }

    private BigDecimal paymentAmount(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT amount
            FROM payments
            WHERE order_id = ?
            """,
            BigDecimal.class,
            orderId
        );
    }

    private String paymentStatus(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM payments
            WHERE order_id = ?
            """,
            String.class,
            orderId
        );
    }

    private String paymentMethod(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT method
            FROM payments
            WHERE order_id = ?
            """,
            String.class,
            orderId
        );
    }

    private BigDecimal reservedQuantity(
        UUID stockItemId,
        UUID stockLocationId
    ) {

        return jdbcTemplate.queryForObject(
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
    }

    private Long reservationCount(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM stock_reservations
            WHERE order_id = ?
            """,
            Long.class,
            orderId
        );
    }

    private BigDecimal reservationQuantity(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(
                SUM(quantity),
                0
            )
            FROM stock_reservations
            WHERE order_id = ?
            """,
            BigDecimal.class,
            orderId
        );
    }

    private UUID reservationStockItem(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT stock_item_id
            FROM stock_reservations
            WHERE order_id = ?
            ORDER BY created_at, id
            LIMIT 1
            """,
            UUID.class,
            orderId
        );
    }

    private String reservationStatus(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM stock_reservations
            WHERE order_id = ?
            ORDER BY created_at, id
            LIMIT 1
            """,
            String.class,
            orderId
        );
    }

    private Long movementCount(
        UUID orderId,
        String movementType
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_movements m
            JOIN stock_reservations r
              ON r.id = m.reference_id
            WHERE r.order_id = ?
              AND m.reference_type =
                    'STOCK_RESERVATION'
              AND m.movement_type = ?
            """,
            Long.class,
            orderId,
            movementType
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

    private record ReservationFixture(
        UUID orderId,
        UUID stockItemId,
        UUID stockLocationId
    ) {
    }
}