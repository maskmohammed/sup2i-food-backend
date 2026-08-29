package com.sup2i.food.pos;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.payment.service.port.PaidOrderKitchenQueue;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
class PosWorkflowE2EIntegrationTest {

    /*
     * Payment -> Kitchen integration already has its own dedicated E2E.
     * This suite validates the POS orchestration while keeping that port
     * isolated, exactly like PaymentE2EIntegrationTest.
     */
    @MockitoBean
    private PaidOrderKitchenQueue paidOrderKitchenQueue;

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
    private String email;
    private String authSessionId;

    @BeforeEach
    void seedTenantAndActor() {

        String suffix =
            suffix();

        organizationId =
            UUID.randomUUID();

        campusId =
            UUID.randomUUID();

        locationId =
            UUID.randomUUID();

        userId =
            UUID.randomUUID();

        email =
            "pos-workflow-"
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
            "POS Workflow Organization " + suffix,
            "PWO" + suffix
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
            "POS Workflow Campus " + suffix,
            "PWC" + suffix
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
            "POS Workflow Location " + suffix,
            "PWL" + suffix
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
            "POS",
            "Workflow"
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
                "pos-workflow-e2e",
                InetAddress.getLoopbackAddress()
            );

        Jwt jwt =
            jwtDecoder.decode(
                tokens.accessToken()
            );

        authSessionId =
            jwt.getClaimAsString(
                "sid"
            );

        assertThat(
            authSessionId
        ).isNotBlank();
    }

    @Test
    void quoteUsesServerPriceAndDoesNotCreateOrder()
        throws Exception {

        SessionFixture session =
            openSession(
                locationId,
                userId,
                "100.00"
            );

        UUID productId =
            insertProduct(
                organizationId,
                "QUOTE",
                "12.50"
            );

        long before =
            posOrderCount();

        performQuote(
            session.sessionId(),
            productId,
            2,
            "order.create"
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.subtotal")
                    .value(25.00)
            )
            .andExpect(
                jsonPath("$.total")
                    .value(25.00)
            )
            .andExpect(
                jsonPath("$.currency")
                    .value("MAD")
            )
            .andExpect(
                jsonPath("$.items.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.items[0].productId")
                    .value(
                        productId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.items[0].unitPrice")
                    .value(12.50)
            )
            .andExpect(
                jsonPath("$.items[0].quantity")
                    .value(2)
            );

        assertThat(
            posOrderCount()
        ).isEqualTo(
            before
        );
    }

    @Test
    void directSaleRequiresCreateAndConfirmPermissions()
        throws Exception {

        SessionFixture session =
            openSession(
                locationId,
                userId,
                "100.00"
            );

        UUID productId =
            insertProduct(
                organizationId,
                "RBAC",
                "10.00"
            );

        performSale(
            session.sessionId(),
            productId,
            1,
            key("sale-rbac"),
            "order.create"
        )
            .andExpect(
                status().isForbidden()
            );

        assertThat(
            posOrderCount()
        ).isZero();
    }

    @Test
    void directSaleCreatesAwaitingPaymentOrderAndStableReplay()
        throws Exception {

        SessionFixture session =
            openSession(
                locationId,
                userId,
                "100.00"
            );

        UUID productId =
            insertProduct(
                organizationId,
                "CREATE",
                "12.50"
            );

        String idempotencyKey =
            key("sale-create");

        MvcResult first =
            performSale(
                session.sessionId(),
                productId,
                2,
                idempotencyKey,
                "order.create",
                "order.confirm"
            )
                .andExpect(
                    status().isCreated()
                )
                .andExpect(
                    jsonPath("$.source")
                        .value("POS")
                )
                .andExpect(
                    jsonPath("$.orderType")
                        .value("POS_DIRECT")
                )
                .andExpect(
                    jsonPath("$.status")
                        .value(
                            "AWAITING_PAYMENT"
                        )
                )
                .andExpect(
                    jsonPath("$.paymentStatus")
                        .value("PENDING")
                )
                .andExpect(
                    jsonPath("$.total")
                        .value(25.00)
                )
                .andReturn();

        UUID orderId =
            latestPosOrderId();

        assertThat(
            orderStatus(
                orderId
            )
        ).isEqualTo(
            "AWAITING_PAYMENT"
        );

        assertThat(
            posOrderCount()
        ).isEqualTo(1L);

        MvcResult replay =
            performSale(
                session.sessionId(),
                productId,
                2,
                idempotencyKey,
                "order.create",
                "order.confirm"
            )
                .andExpect(
                    status().isCreated()
                )
                .andReturn();

        assertThat(
            replay
                .getResponse()
                .getContentAsString()
        ).isEqualTo(
            first
                .getResponse()
                .getContentAsString()
        );

        assertThat(
            posOrderCount()
        ).isEqualTo(1L);
    }

    @Test
    void directSaleCannotUseAnotherCashiersSession()
        throws Exception {

        UUID otherCashier =
            insertUser(
                organizationId,
                "OTHER"
            );

        SessionFixture session =
            openSession(
                locationId,
                otherCashier,
                "50.00"
            );

        UUID productId =
            insertProduct(
                organizationId,
                "CASHIER",
                "10.00"
            );

        performSale(
            session.sessionId(),
            productId,
            1,
            key("sale-other-cashier"),
            "order.create",
            "order.confirm"
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
            posOrderCount()
        ).isZero();
    }

    @Test
    void cashCheckoutPersistsTenderChangeReceiptAndPayment()
        throws Exception {

        SessionFixture session =
            openSession(
                locationId,
                userId,
                "100.00"
            );

        UUID productId =
            insertProduct(
                organizationId,
                "CASH",
                "12.50"
            );

        UUID orderId =
            createDirectSale(
                session.sessionId(),
                productId,
                2,
                key("sale-cash")
            );

        performPayment(
            orderId,
            session.sessionId(),
            "CASH",
            "30.00",
            null,
            key("pay-cash"),
            "payment.collect"
        )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.orderId")
                    .value(
                        orderId.toString()
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
                    .value(25.00)
            )
            .andExpect(
                jsonPath("$.tenderedAmount")
                    .value(30.00)
            )
            .andExpect(
                jsonPath("$.changeAmount")
                    .value(5.00)
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.receipt.orderId")
                    .value(
                        orderId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.receipt.status")
                    .value("ISSUED")
            );

        UUID paymentId =
            paymentId(
                orderId
            );

        assertThat(
            paymentCount(
                orderId
            )
        ).isEqualTo(1L);

        assertThat(
            paymentTendered(
                paymentId
            )
        ).isEqualByComparingTo(
            "30.00"
        );

        assertThat(
            paymentChange(
                paymentId
            )
        ).isEqualByComparingTo(
            "5.00"
        );

        assertThat(
            receiptCount(
                paymentId
            )
        ).isEqualTo(1L);

        assertThat(
            tenderTotal(
                session.sessionId(),
                "CASH"
            )
        ).isEqualByComparingTo(
            "25.00"
        );

        assertThat(
            orderStatus(
                orderId
            )
        ).isEqualTo(
            "PAID"
        );
    }

    @Test
    void cashCheckoutReplayDoesNotDuplicateAnyAccountingSideEffect()
        throws Exception {

        SessionFixture session =
            openSession(
                locationId,
                userId,
                "20.00"
            );

        UUID productId =
            insertProduct(
                organizationId,
                "REPLAY",
                "10.00"
            );

        UUID orderId =
            createDirectSale(
                session.sessionId(),
                productId,
                2,
                key("sale-replay")
            );

        String paymentKey =
            key("payment-replay");

        performPayment(
            orderId,
            session.sessionId(),
            "CASH",
            "25.00",
            null,
            paymentKey,
            "payment.collect"
        )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(false)
            );

        UUID paymentId =
            paymentId(
                orderId
            );

        performPayment(
            orderId,
            session.sessionId(),
            "CASH",
            "25.00",
            null,
            paymentKey,
            "payment.collect"
        )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.paymentId")
                    .value(
                        paymentId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        assertThat(
            paymentCount(
                orderId
            )
        ).isEqualTo(1L);

        assertThat(
            receiptCount(
                paymentId
            )
        ).isEqualTo(1L);

        assertThat(
            tenderTotal(
                session.sessionId(),
                "CASH"
            )
        ).isEqualByComparingTo(
            "20.00"
        );
    }

    @Test
    void cardTpeCheckoutPersistsCardTenderAndReceipt()
        throws Exception {

        SessionFixture session =
            openSession(
                locationId,
                userId,
                "0.00"
            );

        UUID productId =
            insertProduct(
                organizationId,
                "CARD",
                "15.00"
            );

        UUID orderId =
            createDirectSale(
                session.sessionId(),
                productId,
                1,
                key("sale-card")
            );

        performPayment(
            orderId,
            session.sessionId(),
            "CARD_TPE",
            null,
            "TPE-"
                + suffix(),
            key("payment-card"),
            "payment.collect"
        )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.method")
                    .value("CARD_TPE")
            )
            .andExpect(
                jsonPath("$.amount")
                    .value(15.00)
            )
            .andExpect(
                jsonPath("$.tenderedAmount")
                    .value(15.00)
            )
            .andExpect(
                jsonPath("$.changeAmount")
                    .value(0.00)
            );

        UUID paymentId =
            paymentId(
                orderId
            );

        assertThat(
            paymentTendered(
                paymentId
            )
        ).isEqualByComparingTo(
            "15.00"
        );

        assertThat(
            paymentChange(
                paymentId
            )
        ).isEqualByComparingTo(
            "0.00"
        );

        assertThat(
            receiptCount(
                paymentId
            )
        ).isEqualTo(1L);

        assertThat(
            tenderTotal(
                session.sessionId(),
                "CARD_TPE"
            )
        ).isEqualByComparingTo(
            "15.00"
        );
    }

    @Test
    void mobileSnackOrderCanBePaidAtOwnedPosSession()
        throws Exception {

        SessionFixture session =
            openSession(
                locationId,
                userId,
                "50.00"
            );

        UUID productId =
            insertProduct(
                organizationId,
                "MOBILE",
                "12.50"
            );

        UUID orderId =
            insertAwaitingMobileOrder(
                organizationId,
                campusId,
                locationId,
                productId,
                "25.00"
            );

        performPayment(
            orderId,
            session.sessionId(),
            "CASH",
            "25.00",
            null,
            key("mobile-payment"),
            "payment.collect"
        )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.orderId")
                    .value(
                        orderId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.amount")
                    .value(25.00)
            )
            .andExpect(
                jsonPath("$.receipt.orderId")
                    .value(
                        orderId.toString()
                    )
            );

        assertThat(
            orderStatus(
                orderId
            )
        ).isEqualTo(
            "PAID"
        );

        assertThat(
            receiptCount(
                paymentId(
                    orderId
                )
            )
        ).isEqualTo(1L);
    }

    @Test
    void checkoutRejectsOrderFromAnotherPosLocation()
        throws Exception {

        SessionFixture session =
            openSession(
                locationId,
                userId,
                "50.00"
            );

        UUID otherLocation =
            insertLocation(
                campusId,
                "OTHER"
            );

        UUID productId =
            insertProduct(
                organizationId,
                "OTHER-LOCATION",
                "10.00"
            );

        UUID orderId =
            insertAwaitingMobileOrder(
                organizationId,
                campusId,
                otherLocation,
                productId,
                "10.00"
            );

        performPayment(
            orderId,
            session.sessionId(),
            "CASH",
            "10.00",
            null,
            key("different-location"),
            "payment.collect"
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

        assertThat(
            paymentCount(
                orderId
            )
        ).isZero();

        assertThat(
            orderStatus(
                orderId
            )
        ).isEqualTo(
            "AWAITING_PAYMENT"
        );
    }

    @Test
    void exactCloseReconcilesCashAndPreservesP1CloseScope()
        throws Exception {

        SessionFixture session =
            openSession(
                locationId,
                userId,
                "100.00"
            );

        UUID productId =
            insertProduct(
                organizationId,
                "CLOSE",
                "25.00"
            );

        UUID orderId =
            createDirectSale(
                session.sessionId(),
                productId,
                1,
                key("sale-close")
            );

        performPayment(
            orderId,
            session.sessionId(),
            "CASH",
            "25.00",
            null,
            key("pay-close"),
            "payment.collect"
        )
            .andExpect(
                status().isCreated()
            );

        String closeKey =
            key("exact-close");

        performClose(
            session.sessionId(),
            "125.00",
            null,
            closeKey,
            "pos.close"
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("CLOSED")
            )
            .andExpect(
                jsonPath("$.expectedCash")
                    .value(125.00)
            )
            .andExpect(
                jsonPath("$.countedCash")
                    .value(125.00)
            )
            .andExpect(
                jsonPath("$.difference")
                    .value(0.00)
            );

        assertThat(
            sessionStatus(
                session.sessionId()
            )
        ).isEqualTo(
            "CLOSED"
        );

        assertThat(
            supervisorRequired(
                session.sessionId()
            )
        ).isFalse();

        assertThat(
            idempotencyCount(
                "POS_SESSION_CLOSE:"
                    + organizationId,
                closeKey
            )
        ).isEqualTo(1L);
    }

    @Test
    void nonZeroCashDifferenceWithoutCommentIsRejectedAndSessionStaysOpen()
        throws Exception {

        SessionFixture session =
            openSession(
                locationId,
                userId,
                "100.00"
            );

        performClose(
            session.sessionId(),
            "101.00",
            null,
            key("difference-no-comment"),
            "pos.close"
        )
            .andExpect(
                status().isUnprocessableEntity()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "CASH_DIFFERENCE_REASON_REQUIRED"
                    )
            );

        assertThat(
            sessionStatus(
                session.sessionId()
            )
        ).isEqualTo(
            "OPEN"
        );

        assertThat(
            sessionExpectedCash(
                session.sessionId()
            )
        ).isNull();

        assertThat(
            sessionDifference(
                session.sessionId()
            )
        ).isNull();
    }

    @Test
    void nonZeroCashDifferenceWithCommentClosesAndMarksSupervisorRequired()
        throws Exception {

        SessionFixture session =
            openSession(
                locationId,
                userId,
                "100.00"
            );

        performClose(
            session.sessionId(),
            "101.00",
            "Count difference verified",
            key("difference-comment"),
            "pos.close"
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("CLOSED")
            )
            .andExpect(
                jsonPath("$.expectedCash")
                    .value(100.00)
            )
            .andExpect(
                jsonPath("$.difference")
                    .value(1.00)
            );

        assertThat(
            supervisorRequired(
                session.sessionId()
            )
        ).isTrue();

        assertThat(
            sessionDifference(
                session.sessionId()
            )
        ).isEqualByComparingTo(
            "1.00"
        );
    }

    @Test
    void forceCloseRequiresPermissionAndPersistsSupervisorAudit()
        throws Exception {

        SessionFixture session =
            openSession(
                locationId,
                userId,
                "100.00"
            );

        performForceClose(
            session.sessionId(),
            "99.00",
            "Emergency supervisor close",
            key("force-no-permission")
        )
            .andExpect(
                status().isForbidden()
            );

        assertThat(
            sessionStatus(
                session.sessionId()
            )
        ).isEqualTo(
            "OPEN"
        );

        performForceClose(
            session.sessionId(),
            "99.00",
            "Emergency supervisor close",
            key("force-close"),
            "pos.force_close"
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value(
                        "FORCED_CLOSED"
                    )
            )
            .andExpect(
                jsonPath("$.difference")
                    .value(-1.00)
            );

        assertThat(
            forcedClosedBy(
                session.sessionId()
            )
        ).isEqualTo(
            userId
        );

        assertThat(
            validatedBy(
                session.sessionId()
            )
        ).isEqualTo(
            userId
        );

        assertThat(
            supervisorRequired(
                session.sessionId()
            )
        ).isFalse();
    }

    @Test
    void reconciliationNetsRefundsAndManualCashMovements()
        throws Exception {

        SessionFixture session =
            openSession(
                locationId,
                userId,
                "100.00"
            );

        UUID orderId =
            insertPaidPosOrder(
                organizationId,
                campusId,
                locationId,
                "40.00"
            );

        UUID paymentId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO payments (
                id,
                order_id,
                pos_session_id,
                method,
                status,
                amount,
                currency,
                external_reference,
                idempotency_key,
                received_by,
                paid_at
            )
            VALUES (
                ?, ?, ?,
                'CASH',
                'COMPLETED',
                40.00,
                'MAD',
                NULL,
                ?,
                ?,
                CURRENT_TIMESTAMP
            )
            """,
            paymentId,
            orderId,
            session.sessionId(),
            key("recon-payment"),
            userId
        );

        jdbcTemplate.update(
            """
            INSERT INTO refunds (
                id,
                payment_id,
                amount,
                reason,
                status,
                requested_by,
                approved_by,
                completed_at
            )
            VALUES (
                ?, ?,
                10.00,
                'E2E refund',
                'COMPLETED',
                ?,
                ?,
                CURRENT_TIMESTAMP
            )
            """,
            UUID.randomUUID(),
            paymentId,
            userId,
            userId
        );

        insertCashMovement(
            session.sessionId(),
            "CASH_IN",
            "5.00",
            "Float adjustment"
        );

        insertCashMovement(
            session.sessionId(),
            "CASH_OUT",
            "3.00",
            "Petty cash"
        );

        performClose(
            session.sessionId(),
            "132.00",
            null,
            key("recon-close"),
            "pos.close"
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.expectedCash")
                    .value(132.00)
            )
            .andExpect(
                jsonPath("$.difference")
                    .value(0.00)
            );

        assertThat(
            sessionExpectedCash(
                session.sessionId()
            )
        ).isEqualByComparingTo(
            "132.00"
        );

        assertThat(
            sessionDifference(
                session.sessionId()
            )
        ).isEqualByComparingTo(
            "0.00"
        );

        assertThat(
            tenderTotal(
                session.sessionId(),
                "CASH"
            )
        ).isEqualByComparingTo(
            "30.00"
        );
    }

    private UUID createDirectSale(
        UUID sessionId,
        UUID productId,
        int quantity,
        String idempotencyKey
    ) throws Exception {

        performSale(
            sessionId,
            productId,
            quantity,
            idempotencyKey,
            "order.create",
            "order.confirm"
        )
            .andExpect(
                status().isCreated()
            );

        return latestPosOrderId();
    }

    private ResultActions performQuote(
        UUID sessionId,
        UUID productId,
        int quantity,
        String... permissions
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/pos/sales/quote"
            )
                .header(
                    "Authorization",
                    bearer(
                        permissions
                    )
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    saleBody(
                        sessionId,
                        productId,
                        quantity
                    )
                )
        );
    }

    private ResultActions performSale(
        UUID sessionId,
        UUID productId,
        int quantity,
        String idempotencyKey,
        String... permissions
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/pos/sales"
            )
                .header(
                    "Authorization",
                    bearer(
                        permissions
                    )
                )
                .header(
                    "Idempotency-Key",
                    idempotencyKey
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    saleBody(
                        sessionId,
                        productId,
                        quantity
                    )
                )
        );
    }

    private ResultActions performPayment(
        UUID orderId,
        UUID sessionId,
        String method,
        String tenderedAmount,
        String externalReference,
        String idempotencyKey,
        String... permissions
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/pos/payments"
            )
                .header(
                    "Authorization",
                    bearer(
                        permissions
                    )
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
                        sessionId,
                        method,
                        tenderedAmount,
                        externalReference
                    )
                )
        );
    }

    private ResultActions performClose(
        UUID sessionId,
        String countedCash,
        String comment,
        String idempotencyKey,
        String... permissions
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/pos/sessions/"
                    + sessionId
                    + "/close"
            )
                .header(
                    "Authorization",
                    bearer(
                        permissions
                    )
                )
                .header(
                    "Idempotency-Key",
                    idempotencyKey
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    closeBody(
                        countedCash,
                        comment
                    )
                )
        );
    }

    private ResultActions performForceClose(
        UUID sessionId,
        String countedCash,
        String comment,
        String idempotencyKey,
        String... permissions
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/pos/sessions/"
                    + sessionId
                    + "/force-close"
            )
                .header(
                    "Authorization",
                    bearer(
                        permissions
                    )
                )
                .header(
                    "Idempotency-Key",
                    idempotencyKey
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    closeBody(
                        countedCash,
                        comment
                    )
                )
        );
    }

    private String saleBody(
        UUID sessionId,
        UUID productId,
        int quantity
    ) {

        return """
            {
              "posSessionId": "%s",
              "customerNote": "POS E2E",
              "items": [
                {
                  "productId": "%s",
                  "quantity": %d
                }
              ]
            }
            """.formatted(
                sessionId,
                productId,
                quantity
            );
    }

    private String paymentBody(
        UUID orderId,
        UUID sessionId,
        String method,
        String tenderedAmount,
        String externalReference
    ) {

        String tenderedJson =
            tenderedAmount == null
                ? "null"
                : tenderedAmount;

        String externalJson =
            externalReference == null
                ? "null"
                : "\""
                    + externalReference
                    + "\"";

        return """
            {
              "orderId": "%s",
              "posSessionId": "%s",
              "method": "%s",
              "tenderedAmount": %s,
              "externalReference": %s
            }
            """.formatted(
                orderId,
                sessionId,
                method,
                tenderedJson,
                externalJson
            );
    }

    private String closeBody(
        String countedCash,
        String comment
    ) {

        String commentJson =
            comment == null
                ? "null"
                : "\""
                    + comment
                    + "\"";

        return """
            {
              "countedCash": %s,
              "comment": %s
            }
            """.formatted(
                countedCash,
                commentJson
            );
    }

    private SessionFixture openSession(
        UUID tenantLocationId,
        UUID cashierId,
        String openingCash
    ) {

        UUID terminalId =
            UUID.randomUUID();

        UUID sessionId =
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
            terminalId,
            tenantLocationId,
            "TERM-" + suffix(),
            "POS Terminal " + suffix()
        );

        jdbcTemplate.update(
            """
            INSERT INTO pos_sessions (
                id,
                terminal_id,
                cashier_id,
                opening_cash,
                status
            )
            VALUES (
                ?, ?, ?, ?, 'OPEN'
            )
            """,
            sessionId,
            terminalId,
            cashierId,
            new BigDecimal(
                openingCash
            )
        );

        return new SessionFixture(
            sessionId,
            terminalId
        );
    }

    private UUID insertProduct(
        UUID tenantOrganizationId,
        String prefix,
        String price
    ) {

        UUID categoryId =
            UUID.randomUUID();

        UUID productId =
            UUID.randomUUID();

        String localSuffix =
            suffix();

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
            VALUES (
                ?, ?, ?, ?, TRUE, 0
            )
            """,
            categoryId,
            tenantOrganizationId,
            prefix + " Category",
            (
                "pos-"
                    + localSuffix
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
                ?,
                0,
                FALSE,
                FALSE,
                TRUE
            )
            """,
            productId,
            tenantOrganizationId,
            categoryId,
            "SKU-" + localSuffix,
            prefix + " Product",
            new BigDecimal(
                price
            )
        );

        return productId;
    }

    private UUID insertUser(
        UUID tenantOrganizationId,
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        String localSuffix =
            suffix();

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
            VALUES (
                ?, ?, ?, ?, ?, 'ACTIVE'
            )
            """,
            id,
            tenantOrganizationId,
            "pos-"
                + localSuffix
                + "@sup2i.test",
            prefix,
            "Cashier"
        );

        return id;
    }

    private UUID insertLocation(
        UUID tenantCampusId,
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        String localSuffix =
            suffix();

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
                ?, ?, ?, ?,
                'SNACK',
                TRUE
            )
            """,
            id,
            tenantCampusId,
            prefix + " Location",
            "LOC" + localSuffix
        );

        return id;
    }

    private UUID insertAwaitingMobileOrder(
        UUID tenantOrganizationId,
        UUID tenantCampusId,
        UUID tenantLocationId,
        UUID productId,
        String total
    ) {

        UUID orderId =
            UUID.randomUUID();

        UUID itemId =
            UUID.randomUUID();

        String localSuffix =
            suffix();

        BigDecimal amount =
            new BigDecimal(
                total
            );

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
                'AWAITING_PAYMENT',
                ?,
                0.00,
                0.00,
                ?,
                'MAD',
                CURRENT_TIMESTAMP
                    + INTERVAL '15 minutes',
                'MOBILE_SNACK',
                'PENDING',
                'POS mobile payment E2E'
            )
            """,
            orderId,
            tenantOrganizationId,
            tenantCampusId,
            tenantLocationId,
            "MOB-" + localSuffix,
            amount,
            amount
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
                ?,
                1,
                0.00,
                ?,
                0.00,
                0.00,
                NULL
            )
            """,
            itemId,
            orderId,
            productId,
            "Mobile Product",
            "MOB-SKU-" + localSuffix,
            amount,
            amount
        );

        return orderId;
    }

    private UUID insertPaidPosOrder(
        UUID tenantOrganizationId,
        UUID tenantCampusId,
        UUID tenantLocationId,
        String total
    ) {

        UUID orderId =
            UUID.randomUUID();

        String localSuffix =
            suffix();

        BigDecimal amount =
            new BigDecimal(
                total
            );

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
                order_type,
                payment_status,
                paid_at,
                customer_note
            )
            VALUES (
                ?, ?, ?, ?, NULL,
                ?,
                CURRENT_DATE,
                'POS',
                'PAID',
                ?,
                0.00,
                0.00,
                ?,
                'MAD',
                'POS_DIRECT',
                'COMPLETED',
                CURRENT_TIMESTAMP,
                'Reconciliation E2E'
            )
            """,
            orderId,
            tenantOrganizationId,
            tenantCampusId,
            tenantLocationId,
            "REC-" + localSuffix,
            amount,
            amount
        );

        return orderId;
    }

    private void insertCashMovement(
        UUID sessionId,
        String type,
        String amount,
        String reason
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO cash_movements (
                id,
                pos_session_id,
                type,
                amount,
                reason,
                performed_by
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            sessionId,
            type,
            new BigDecimal(
                amount
            ),
            reason,
            userId
        );
    }

    private long posOrderCount() {

        Long value =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM orders
                WHERE organization_id = ?
                  AND source = 'POS'
                """,
                Long.class,
                organizationId
            );

        return value == null
            ? 0L
            : value;
    }

    private UUID latestPosOrderId() {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM orders
            WHERE organization_id = ?
              AND source = 'POS'
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """,
            UUID.class,
            organizationId
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

    private long paymentCount(
        UUID orderId
    ) {

        Long value =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM payments
                WHERE order_id = ?
                """,
                Long.class,
                orderId
            );

        return value == null
            ? 0L
            : value;
    }

    private UUID paymentId(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM payments
            WHERE order_id = ?
            ORDER BY created_at ASC, id ASC
            LIMIT 1
            """,
            UUID.class,
            orderId
        );
    }

    private BigDecimal paymentTendered(
        UUID paymentId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT tendered_amount
            FROM payments
            WHERE id = ?
            """,
            BigDecimal.class,
            paymentId
        );
    }

    private BigDecimal paymentChange(
        UUID paymentId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT change_amount
            FROM payments
            WHERE id = ?
            """,
            BigDecimal.class,
            paymentId
        );
    }

    private long receiptCount(
        UUID paymentId
    ) {

        Long value =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM sales_receipts
                WHERE payment_id = ?
                """,
                Long.class,
                paymentId
            );

        return value == null
            ? 0L
            : value;
    }

    private BigDecimal tenderTotal(
        UUID sessionId,
        String method
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT theoretical_amount
            FROM pos_session_tender_totals
            WHERE pos_session_id = ?
              AND payment_method = ?
            """,
            BigDecimal.class,
            sessionId,
            method
        );
    }

    private String sessionStatus(
        UUID sessionId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM pos_sessions
            WHERE id = ?
            """,
            String.class,
            sessionId
        );
    }

    private BigDecimal sessionExpectedCash(
        UUID sessionId
    ) {

        List<BigDecimal> values =
            jdbcTemplate.query(
                """
                SELECT expected_cash
                FROM pos_sessions
                WHERE id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getBigDecimal(
                        "expected_cash"
                    ),
                sessionId
            );

        return values.get(0);
    }

    private BigDecimal sessionDifference(
        UUID sessionId
    ) {

        List<BigDecimal> values =
            jdbcTemplate.query(
                """
                SELECT difference
                FROM pos_sessions
                WHERE id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getBigDecimal(
                        "difference"
                    ),
                sessionId
            );

        return values.get(0);
    }

    private boolean supervisorRequired(
        UUID sessionId
    ) {

        Boolean value =
            jdbcTemplate.queryForObject(
                """
                SELECT supervisor_required
                FROM pos_sessions
                WHERE id = ?
                """,
                Boolean.class,
                sessionId
            );

        return Boolean.TRUE.equals(
            value
        );
    }

    private UUID forcedClosedBy(
        UUID sessionId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT forced_closed_by
            FROM pos_sessions
            WHERE id = ?
            """,
            UUID.class,
            sessionId
        );
    }

    private UUID validatedBy(
        UUID sessionId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT validated_by
            FROM pos_sessions
            WHERE id = ?
            """,
            UUID.class,
            sessionId
        );
    }

    private long idempotencyCount(
        String scope,
        String idempotencyKey
    ) {

        Long value =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM idempotency_records
                WHERE scope = ?
                  AND idempotency_key = ?
                """,
                Long.class,
                scope,
                idempotencyKey
            );

        return value == null
            ? 0L
            : value;
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

    private String key(
        String prefix
    ) {

        return prefix
            + "-"
            + UUID.randomUUID();
    }

    private String suffix() {

        return UUID
            .randomUUID()
            .toString()
            .replace(
                "-",
                ""
            )
            .substring(
                0,
                8
            )
            .toUpperCase(
                Locale.ROOT
            );
    }

    private record SessionFixture(
        UUID sessionId,
        UUID terminalId
    ) {
    }
}