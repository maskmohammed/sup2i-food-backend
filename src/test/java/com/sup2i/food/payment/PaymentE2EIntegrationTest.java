package com.sup2i.food.payment;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.payment.domain.PaymentMethod;
import com.sup2i.food.payment.exception.PaymentConflictException;
import com.sup2i.food.payment.service.PaymentCaptureCommand;
import com.sup2i.food.payment.service.PaymentService;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
class PaymentE2EIntegrationTest {

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
    private PaymentService paymentService;

    private UUID organizationId;
    private UUID campusId;
    private UUID locationId;

    private Actor actor;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "PAY"
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
                "PAY"
            );
    }


    // =========================================================
    // 01 - AUTHENTICATION
    // =========================================================

    @Test
    void unauthenticatedPaymentIsRejected()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/payments"
                )
                    .header(
                        "Idempotency-Key",
                        key()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        paymentBody(
                            UUID.randomUUID(),
                            "CASH",
                            "10.00",
                            null,
                            null
                        )
                    )
            )
            .andExpect(
                status().isUnauthorized()
            );
    }


    // =========================================================
    // 02 - RBAC
    // =========================================================

    @Test
    void paymentCollectPermissionIsRequired()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/payments"
                )
                    .with(
                        jwt()
                            .jwt(
                                token ->
                                    token.subject(
                                        actor.userId()
                                            .toString()
                                    ).claim("sid", activeSessionId())
                            )
                    )
                    .header(
                        "Idempotency-Key",
                        key()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        paymentBody(
                            UUID.randomUUID(),
                            "CASH",
                            "10.00",
                            null,
                            null
                        )
                    )
            )
            .andExpect(
                status().isForbidden()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "PERMISSION_DENIED"
                    )
            );
    }


    // =========================================================
    // 03 - HTTP CONTRACT
    // =========================================================

    @Test
    void httpContractRejectsUnsupportedMethodAndInvalidAmount()
        throws Exception {

        mockMvc.perform(
                paymentRequest(
                    UUID.randomUUID(),
                    key(),
                    "ONLINE",
                    "10.00",
                    null,
                    null
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

        mockMvc.perform(
                paymentRequest(
                    UUID.randomUUID(),
                    key(),
                    "CASH",
                    "0.00",
                    null,
                    null
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
    }


    // =========================================================
    // 04 - IDEMPOTENCY HEADER
    // =========================================================

    @Test
    void missingIdempotencyKeyUsesStableValidationError()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "NO-IDEM",
                1
            );

        mockMvc.perform(
                post(
                    "/api/v1/payments"
                )
                    .with(
                        paymentJwt()
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        paymentBody(
                            fixture.orderId(),
                            "CASH",
                            "10.00",
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
                    .value(
                        "VALIDATION_ERROR"
                    )
            );

        assertThat(
            paymentCount(
                fixture.orderId()
            )
        ).isZero();
    }


    // =========================================================
    // 05 - SERVER TOTAL IS SOURCE OF TRUTH
    // =========================================================

    @Test
    void paymentAmountMismatchRollsBackWithoutSideEffects()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "AMOUNT",
                2
            );

        BigDecimal physicalBefore =
            physicalQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            );

        BigDecimal reservedBefore =
            reservedQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            );

        mockMvc.perform(
                paymentRequest(
                    fixture.orderId(),
                    key(),
                    "CASH",
                    "19.99",
                    null,
                    null
                )
            )
            .andExpect(
                status().isUnprocessableEntity()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "PAYMENT_AMOUNT_MISMATCH"
                    )
            );

        assertThat(
            paymentCount(
                fixture.orderId()
            )
        ).isZero();

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "AWAITING_PAYMENT"
        );

        assertThat(
            reservationStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "ACTIVE"
        );

        assertThat(
            physicalQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            )
        ).isEqualByComparingTo(
            physicalBefore
        );

        assertThat(
            reservedQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            )
        ).isEqualByComparingTo(
            reservedBefore
        );
    }


    // =========================================================
    // 06 - CASH / PACKAGED / IDEMPOTENT REPLAY
    // =========================================================

    @Test
    void cashPackagedPaymentConsumesStockExactlyOnceAndReplays()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "CASH",
                3
            );

        BigDecimal physicalBefore =
            physicalQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            );

        BigDecimal reservedBefore =
            reservedQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            );

        String idempotencyKey =
            key();

        String paymentId =
            mockMvc.perform(
                    paymentRequest(
                        fixture.orderId(),
                        idempotencyKey,
                        "CASH",
                        "30.00",
                        "CASH-EXT",
                        null
                    )
                )
                .andExpect(
                    status().isCreated()
                )
                .andExpect(
                    jsonPath("$.orderId")
                        .value(
                            fixture.orderId()
                                .toString()
                        )
                )
                .andExpect(
                    jsonPath("$.method")
                        .value("CASH")
                )
                .andExpect(
                    jsonPath("$.status")
                        .value("COMPLETED")
                )
                .andExpect(
                    jsonPath("$.amount")
                        .value(30.00)
                )
                .andExpect(
                    jsonPath("$.currency")
                        .value("MAD")
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID persistedPaymentId =
            paymentId(
                fixture.orderId()
            );

        assertThat(
            paymentId
        ).contains(
            persistedPaymentId.toString()
        );

        assertThat(
            paymentCount(
                fixture.orderId()
            )
        ).isEqualTo(1L);

        assertThat(
            paymentStatus(
                persistedPaymentId
            )
        ).isEqualTo(
            "COMPLETED"
        );

        assertThat(
            paymentEventCount(
                persistedPaymentId,
                "CAPTURE_COMPLETED"
            )
        ).isEqualTo(1L);

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "PAID"
        );

        assertThat(
            reservationStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "CONSUMED"
        );

        assertThat(
            reservationConsumedAt(
                fixture.orderId()
            )
        ).isNotNull();

        assertThat(
            physicalQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            )
        ).isEqualByComparingTo(
            physicalBefore.subtract(
                new BigDecimal("3.000")
            )
        );

        assertThat(
            reservedQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            )
        ).isEqualByComparingTo(
            reservedBefore.subtract(
                new BigDecimal("3.000")
            )
        );

        assertThat(
            paymentSaleMovementCount(
                persistedPaymentId
            )
        ).isEqualTo(1L);

        assertThat(
            paymentPhysicalDelta(
                persistedPaymentId
            )
        ).isEqualByComparingTo(
            "-3.000"
        );

        assertThat(
            paymentReservedDelta(
                persistedPaymentId
            )
        ).isEqualByComparingTo(
            "-3.000"
        );

        mockMvc.perform(
                paymentRequest(
                    fixture.orderId(),
                    idempotencyKey,
                    "CASH",
                    "30.00",
                    "CASH-EXT",
                    null
                )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.id")
                    .value(
                        persistedPaymentId
                            .toString()
                    )
            );

        assertThat(
            paymentCount(
                fixture.orderId()
            )
        ).isEqualTo(1L);

        assertThat(
            paymentEventCount(
                persistedPaymentId,
                "CAPTURE_COMPLETED"
            )
        ).isEqualTo(1L);

        assertThat(
            paymentSaleMovementCount(
                persistedPaymentId
            )
        ).isEqualTo(1L);
    }


    // =========================================================
    // 07 - IDEMPOTENCY CONFLICT
    // =========================================================

    @Test
    void sameIdempotencyKeyWithDifferentPayloadConflicts()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "IDEM",
                1
            );

        String idempotencyKey =
            key();

        mockMvc.perform(
                paymentRequest(
                    fixture.orderId(),
                    idempotencyKey,
                    "CASH",
                    "10.00",
                    "FIRST",
                    null
                )
            )
            .andExpect(
                status().isCreated()
            );

        mockMvc.perform(
                paymentRequest(
                    fixture.orderId(),
                    idempotencyKey,
                    "CASH",
                    "10.00",
                    "DIFFERENT",
                    null
                )
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "IDEMPOTENCY_CONFLICT"
                    )
            );

        assertThat(
            paymentCount(
                fixture.orderId()
            )
        ).isEqualTo(1L);
    }


    // =========================================================
    // 08 - CARD TPE
    // =========================================================

    @Test
    void cardTpePaymentIsAccepted()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "CARD",
                1
            );

        mockMvc.perform(
                paymentRequest(
                    fixture.orderId(),
                    key(),
                    "CARD_TPE",
                    "10.00",
                    "TPE-001",
                    null
                )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.method")
                    .value("CARD_TPE")
            )
            .andExpect(
                jsonPath("$.status")
                    .value("COMPLETED")
            );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "PAID"
        );
    }


    // =========================================================
    // 09 - PREPARED PRODUCT
    // =========================================================

    @Test
    void preparedPaymentKeepsReservationActiveAndPhysicalUntouched()
        throws Exception {

        ReservationFixture fixture =
            awaitingPreparedOrder(
                "PREP",
                2
            );

        BigDecimal physicalBefore =
            physicalQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            );

        BigDecimal reservedBefore =
            reservedQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            );

        mockMvc.perform(
                paymentRequest(
                    fixture.orderId(),
                    key(),
                    "CASH",
                    "30.00",
                    null,
                    null
                )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("COMPLETED")
            );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "PAID"
        );

        assertThat(
            reservationStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "ACTIVE"
        );

        assertThat(
            physicalQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            )
        ).isEqualByComparingTo(
            physicalBefore
        );

        assertThat(
            reservedQuantity(
                fixture.stockItemId(),
                fixture.stockLocationId()
            )
        ).isEqualByComparingTo(
            reservedBefore
        );

        UUID paymentId =
            paymentId(
                fixture.orderId()
            );

        assertThat(
            paymentSaleMovementCount(
                paymentId
            )
        ).isZero();
    }


    // =========================================================
    // 10 - POS SESSION
    // =========================================================

    @Test
    void posSessionMustBeOpenAndBelongToSameOrganization()
        throws Exception {

        ReservationFixture accepted =
            awaitingPackagedOrder(
                "POS-OPEN",
                1
            );

        UUID openSession =
            insertPosSession(
                locationId,
                actor.userId(),
                "OPEN"
            );

        mockMvc.perform(
                paymentRequest(
                    accepted.orderId(),
                    key(),
                    "CASH",
                    "10.00",
                    null,
                    openSession
                )
            )
            .andExpect(
                status().isCreated()
            );

        assertThat(
            paymentPosSession(
                paymentId(
                    accepted.orderId()
                )
            )
        ).isEqualTo(
            openSession
        );


        ReservationFixture closed =
            awaitingPackagedOrder(
                "POS-CLOSED",
                1
            );

        UUID closedSession =
            insertPosSession(
                locationId,
                actor.userId(),
                "CLOSED"
            );

        mockMvc.perform(
                paymentRequest(
                    closed.orderId(),
                    key(),
                    "CASH",
                    "10.00",
                    null,
                    closedSession
                )
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "POS_SESSION_NOT_OPEN"
                    )
            );


        cancel(
            closed.orderId(),
            actor
        )
            .andExpect(
                status().isOk()
            );

        assertThat(
            orderStatus(
                closed.orderId()
            )
        ).isEqualTo(
            "CANCELLED"
        );

        assertThat(
            reservationStatus(
                closed.orderId()
            )
        ).isEqualTo(
            "RELEASED"
        );

        UUID foreignOrganization =
            insertOrganization(
                "POS-FOREIGN"
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
                "SNACK",
                true
            );

        UUID foreignSession =
            insertPosSession(
                foreignLocation,
                actor.userId(),
                "OPEN"
            );

        ReservationFixture foreign =
            awaitingPackagedOrder(
                "POS-FOREIGN-ORDER",
                1
            );

        mockMvc.perform(
                paymentRequest(
                    foreign.orderId(),
                    key(),
                    "CASH",
                    "10.00",
                    null,
                    foreignSession
                )
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "POS_SESSION_NOT_OPEN"
                    )
            );

        assertThat(
            paymentCount(
                foreign.orderId()
            )
        ).isZero();
    }


    // =========================================================
    // 11 - TYPED ORDER ERRORS
    // =========================================================

    @Test
    void unknownAndExpiredOrdersUseStableTypedErrors()
        throws Exception {

        mockMvc.perform(
                paymentRequest(
                    UUID.randomUUID(),
                    key(),
                    "CASH",
                    "10.00",
                    null,
                    null
                )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "ORDER_NOT_FOUND"
                    )
            );

        ReservationFixture expired =
            awaitingPackagedOrder(
                "EXPIRED",
                1
            );

        jdbcTemplate.update(
            """
            UPDATE orders
            SET payment_expires_at =
                CURRENT_TIMESTAMP
                - INTERVAL '1 minute'
            WHERE id = ?
            """,
            expired.orderId()
        );

        mockMvc.perform(
                paymentRequest(
                    expired.orderId(),
                    key(),
                    "CASH",
                    "10.00",
                    null,
                    null
                )
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "ORDER_EXPIRED"
                    )
            );

        assertThat(
            paymentCount(
                expired.orderId()
            )
        ).isZero();
    }


    // =========================================================
    // 12 - CONCURRENT DOUBLE CAPTURE
    // =========================================================

    @Test
    void concurrentDifferentKeysCannotDoubleCaptureOrder()
        throws Exception {

        ReservationFixture fixture =
            awaitingPackagedOrder(
                "RACE",
                1
            );

        CountDownLatch ready =
            new CountDownLatch(2);

        CountDownLatch start =
            new CountDownLatch(1);

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        try {

            List<Future<Integer>> futures =
                new ArrayList<>();

            for (
                int index = 0;
                index < 2;
                index++
            ) {

                final int suffix =
                    index;

                futures.add(
                    executor.submit(
                        () -> {

                            ready.countDown();
                            start.await();

                            try {

                                paymentService.capture(
                                    actor.userId(),
                                    fixture.orderId(),
                                    new PaymentCaptureCommand(
                                        PaymentMethod.CASH,
                                        "race-key-" + suffix,
                                        new BigDecimal(
                                            "10.00"
                                        ),
                                        null,
                                        null
                                    )
                                );

                                return 201;

                            } catch (
                                PaymentConflictException exception
                            ) {

                                return 409;
                            }
                        }
                    )
                );
            }

            ready.await();

            start.countDown();

            List<Integer> statuses =
                new ArrayList<>();

            for (
                Future<Integer> future
                : futures
            ) {

                statuses.add(
                    future.get()
                );
            }

            Collections.sort(
                statuses
            );

            assertThat(
                statuses
            ).containsExactly(
                201,
                409
            );

        } finally {

            executor.shutdownNow();
        }

        assertThat(
            paymentCount(
                fixture.orderId()
            )
        ).isEqualTo(1L);

        UUID paymentId =
            paymentId(
                fixture.orderId()
            );

        assertThat(
            paymentSaleMovementCount(
                paymentId
            )
        ).isEqualTo(1L);

        assertThat(
            reservationStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "CONSUMED"
        );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        ).isEqualTo(
            "PAID"
        );
    }


    // =========================================================
    // PAYMENT HTTP
    // =========================================================

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        paymentRequest(
            UUID orderId,
            String idempotencyKey,
            String method,
            String amount,
            String externalReference,
            UUID posSessionId
        ) {

        return post(
            "/api/v1/payments"
        )
            .with(
                paymentJwt()
            )
            .header(
                "Idempotency-Key",
                idempotencyKey
            )
            .contentType(
                MediaType.APPLICATION_JSON
            )
            .content(
                paymentBody(
                    orderId,
                    method,
                    amount,
                    externalReference,
                    posSessionId
                )
            );
    }

    private String activeSessionId() {

        String sessionId =
            jwtDecoder
                .decode(
                    actor.accessToken()
                )
                .getClaimAsString(
                    "sid"
                );

        if (sessionId == null) {
            throw new IllegalStateException(
                "Actor access token has no sid."
            );
        }

        return sessionId;
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor
        paymentJwt() {

        return jwt()
            .jwt(
                token ->
                    token.subject(
                        actor.userId()
                            .toString()
                    ).claim("sid", activeSessionId())
            )
            .authorities(
                new SimpleGrantedAuthority(
                    "payment.collect"
                )
            );
    }

    private String paymentBody(
        UUID orderId,
        String method,
        String amount,
        String externalReference,
        UUID posSessionId
    ) {

        String external =
            externalReference == null
                ? "null"
                : "\"" + externalReference + "\"";

        String session =
            posSessionId == null
                ? "null"
                : "\"" + posSessionId + "\"";

        return """
            {
              "orderId": "%s",
              "method": "%s",
              "amount": %s,
              "externalReference": %s,
              "posSessionId": %s
            }
            """.formatted(
                orderId,
                method,
                amount,
                external,
                session
            );
    }


    // =========================================================
    // PREPARED FIXTURE
    // =========================================================

    private ReservationFixture awaitingPreparedOrder(
        String prefix,
        int quantity
    ) throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PREPARED",
                prefix,
                "15.00"
            );

        UUID ingredientId =
            insertIngredient(
                organizationId,
                prefix + "-ING",
                "GRAM"
            );

        insertRecipe(
            productId,
            null,
            ingredientId,
            "2.000",
            "GRAM",
            "0.1000"
        );

        UUID stockItemId =
            insertIngredientStockItem(
                organizationId,
                ingredientId,
                "GRAM"
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
            "50.000",
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
            );

        return new ReservationFixture(
            orderId,
            stockItemId,
            stockLocationId
        );
    }


    // =========================================================
    // POS FIXTURE
    // =========================================================

    private UUID insertPosSession(
        UUID selectedLocationId,
        UUID cashierId,
        String status
    ) {

        String suffix =
            randomSuffix();

        UUID terminalId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO pos_terminals (
                id,
                location_id,
                code,
                name,
                software_type,
                is_active
            )
            VALUES (?, ?, ?, ?, 'SUP2I_POS', TRUE)
            """,
            terminalId,
            selectedLocationId,
            "T-" + suffix,
            "Terminal " + suffix
        );

        UUID sessionId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO pos_sessions (
                id,
                terminal_id,
                cashier_id,
                opening_cash,
                status,
                closed_at
            )
            VALUES (
                ?,
                ?,
                ?,
                0,
                ?,
                CASE
                    WHEN ? = 'OPEN'
                    THEN NULL
                    ELSE CURRENT_TIMESTAMP
                END
            )
            """,
            sessionId,
            terminalId,
            cashierId,
            status,
            status
        );

        return sessionId;
    }


    // =========================================================
    // PAYMENT DB ASSERTIONS
    // =========================================================

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

    private UUID paymentId(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM payments
            WHERE order_id = ?
            ORDER BY created_at, id
            LIMIT 1
            """,
            UUID.class,
            orderId
        );
    }

    private String paymentStatus(
        UUID paymentId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM payments
            WHERE id = ?
            """,
            String.class,
            paymentId
        );
    }

    private UUID paymentPosSession(
        UUID paymentId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT pos_session_id
            FROM payments
            WHERE id = ?
            """,
            UUID.class,
            paymentId
        );
    }

    private Long paymentEventCount(
        UUID paymentId,
        String eventType
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM payment_events
            WHERE payment_id = ?
              AND event_type = ?
            """,
            Long.class,
            paymentId,
            eventType
        );
    }

    private BigDecimal physicalQuantity(
        UUID stockItemId,
        UUID stockLocationId
    ) {

        return jdbcTemplate.queryForObject(
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
    }

    private java.time.OffsetDateTime reservationConsumedAt(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT consumed_at
            FROM stock_reservations
            WHERE order_id = ?
            ORDER BY created_at, id
            LIMIT 1
            """,
            java.time.OffsetDateTime.class,
            orderId
        );
    }

    private Long paymentSaleMovementCount(
        UUID paymentId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM inventory_movements
            WHERE reference_type = 'PAYMENT'
              AND reference_id = ?
              AND movement_type = 'SALE_OUT'
            """,
            Long.class,
            paymentId
        );
    }

    private BigDecimal paymentPhysicalDelta(
        UUID paymentId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(
                SUM(physical_delta),
                0
            )
            FROM inventory_movements
            WHERE reference_type = 'PAYMENT'
              AND reference_id = ?
              AND movement_type = 'SALE_OUT'
            """,
            BigDecimal.class,
            paymentId
        );
    }

    private BigDecimal paymentReservedDelta(
        UUID paymentId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(
                SUM(reserved_delta),
                0
            )
            FROM inventory_movements
            WHERE reference_type = 'PAYMENT'
              AND reference_id = ?
              AND movement_type = 'SALE_OUT'
            """,
            BigDecimal.class,
            paymentId
        );
    }

    private String key() {

        return "pay-"
            + UUID.randomUUID();
    }

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
