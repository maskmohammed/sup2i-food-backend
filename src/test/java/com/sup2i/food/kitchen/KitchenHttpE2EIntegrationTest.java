package com.sup2i.food.kitchen;

import tools.jackson.databind.json.JsonMapper;
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
import org.springframework.test.web.servlet.MvcResult;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
class KitchenHttpE2EIntegrationTest {

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
    private JsonMapper objectMapper;

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
            "kitchen-http-"
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
            "Kitchen HTTP " + suffix,
            "KH" + suffix
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
            "Kitchen",
            "HTTP"
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "kitchen-http-e2e",
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

        assertThat(authSessionId)
            .isNotBlank();
    }

    @Test
    void getRequiresKitchenReadAndFiltersByStatus()
        throws Exception {

        TicketFixture fixture =
            insertQueuedTicket(
                organizationId
            );

        mockMvc.perform(
                get(
                    "/api/v1/kitchen/tickets"
                )
                    .queryParam(
                        "kitchenLocationId",
                        fixture
                            .kitchenLocationId()
                            .toString()
                    )
                    .header(
                        "Authorization",
                        bearer(
                            "kitchen.prepare"
                        )
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        String queuedBody =
            mockMvc.perform(
                    get(
                        "/api/v1/kitchen/tickets"
                    )
                        .queryParam(
                            "kitchenLocationId",
                            fixture
                                .kitchenLocationId()
                                .toString()
                        )
                        .queryParam(
                            "status",
                            "QUEUED"
                        )
                        .header(
                            "Authorization",
                            bearer(
                                "kitchen.read"
                            )
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(queuedBody)
            .contains(
                fixture
                    .ticketId()
                    .toString()
            )
            .contains(
                fixture
                    .lineId()
                    .toString()
            )
            .contains(
                "QUEUED"
            );

        String readyBody =
            mockMvc.perform(
                    get(
                        "/api/v1/kitchen/tickets"
                    )
                        .queryParam(
                            "kitchenLocationId",
                            fixture
                                .kitchenLocationId()
                                .toString()
                        )
                        .queryParam(
                            "status",
                            "READY"
                        )
                        .header(
                            "Authorization",
                            bearer(
                                "kitchen.read"
                            )
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(readyBody)
            .isEqualTo(
                "[]"
            );
    }

    @Test
    void getRejectsMissingInvalidLocationAndInvalidStatus()
        throws Exception {

        TicketFixture fixture =
            insertQueuedTicket(
                organizationId
            );

        mockMvc.perform(
                get(
                    "/api/v1/kitchen/tickets"
                )
                    .header(
                        "Authorization",
                        bearer(
                            "kitchen.read"
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

        mockMvc.perform(
                get(
                    "/api/v1/kitchen/tickets"
                )
                    .queryParam(
                        "kitchenLocationId",
                        "not-a-uuid"
                    )
                    .header(
                        "Authorization",
                        bearer(
                            "kitchen.read"
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

        mockMvc.perform(
                get(
                    "/api/v1/kitchen/tickets"
                )
                    .queryParam(
                        "kitchenLocationId",
                        fixture
                            .kitchenLocationId()
                            .toString()
                    )
                    .queryParam(
                        "status",
                        "NOT_A_KITCHEN_STATUS"
                    )
                    .header(
                        "Authorization",
                        bearer(
                            "kitchen.read"
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
    }

    @Test
    void startAndReadyUseSeparatePermissions()
        throws Exception {

        TicketFixture fixture =
            insertQueuedTicket(
                organizationId
            );

        mockMvc.perform(
                post(
                    "/api/v1/kitchen/tickets/{ticketId}/start",
                    fixture.ticketId()
                )
                    .header(
                        "Authorization",
                        bearer(
                            "kitchen.ready"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        key(
                            "rbac-start"
                        )
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                post(
                    "/api/v1/kitchen/tickets/{ticketId}/ready",
                    fixture.ticketId()
                )
                    .header(
                        "Authorization",
                        bearer(
                            "kitchen.prepare"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        key(
                            "rbac-ready"
                        )
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        )
            .isEqualTo(
                "QUEUED"
            );

        assertThat(
            ticketStatus(
                fixture.ticketId()
            )
        )
            .isEqualTo(
                "QUEUED"
            );
    }

    @Test
    void startMutatesWorkflowAndPersistsDurableIdempotency()
        throws Exception {

        TicketFixture fixture =
            insertQueuedTicket(
                organizationId
            );

        String idempotencyKey =
            key(
                "start-success"
            );

        String body =
            mockMvc.perform(
                    post(
                        "/api/v1/kitchen/tickets/{ticketId}/start",
                        fixture.ticketId()
                    )
                        .header(
                            "Authorization",
                            bearer(
                                "kitchen.prepare"
                            )
                        )
                        .header(
                            "Idempotency-Key",
                            idempotencyKey
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
            .contains(
                fixture
                    .ticketId()
                    .toString()
            )
            .contains(
                "PREPARING"
            );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        )
            .isEqualTo(
                "PREPARING"
            );

        assertThat(
            ticketStatus(
                fixture.ticketId()
            )
        )
            .isEqualTo(
                "PREPARING"
            );

        assertThat(
            lineStatus(
                fixture.lineId()
            )
        )
            .isEqualTo(
                "PREPARING"
            );

        assertThat(
            startedTimestampCount(
                fixture
            )
        )
            .isEqualTo(
                2L
            );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "QUEUED",
                "PREPARING"
            )
        )
            .isEqualTo(
                1L
            );

        assertThat(
            idempotencyCount(
                startScope(),
                idempotencyKey
            )
        )
            .isEqualTo(
                1L
            );

        assertThat(
            validIdempotencyMetadataCount(
                startScope(),
                idempotencyKey,
                fixture.ticketId()
            )
        )
            .isEqualTo(
                1L
            );
    }

    @Test
    void startSameKeyReplayReturnsOriginalResponseWithoutMutation()
        throws Exception {

        TicketFixture fixture =
            insertQueuedTicket(
                organizationId
            );

        String idempotencyKey =
            key(
                "start-replay"
            );

        String firstBody =
            mockMvc.perform(
                    post(
                        "/api/v1/kitchen/tickets/{ticketId}/start",
                        fixture.ticketId()
                    )
                        .header(
                            "Authorization",
                            bearer(
                                "kitchen.prepare"
                            )
                        )
                        .header(
                            "Idempotency-Key",
                            idempotencyKey
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        long historyBefore =
            transitionCount(
                fixture.orderId(),
                "QUEUED",
                "PREPARING"
            );

        long idempotencyBefore =
            idempotencyCount(
                startScope(),
                idempotencyKey
            );

        String replayBody =
            mockMvc.perform(
                    post(
                        "/api/v1/kitchen/tickets/{ticketId}/start",
                        fixture.ticketId()
                    )
                        .header(
                            "Authorization",
                            bearer(
                                "kitchen.prepare"
                            )
                        )
                        .header(
                            "Idempotency-Key",
                            idempotencyKey
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertSameJson(
            firstBody,
            replayBody
        );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "QUEUED",
                "PREPARING"
            )
        )
            .isEqualTo(
                historyBefore
            )
            .isEqualTo(
                1L
            );

        assertThat(
            idempotencyCount(
                startScope(),
                idempotencyKey
            )
        )
            .isEqualTo(
                idempotencyBefore
            )
            .isEqualTo(
                1L
            );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        )
            .isEqualTo(
                "PREPARING"
            );
    }

    @Test
    void sameStartKeyForDifferentTicketReturnsIdempotencyConflict()
        throws Exception {

        TicketFixture first =
            insertQueuedTicket(
                organizationId
            );

        TicketFixture second =
            insertQueuedTicket(
                organizationId
            );

        String idempotencyKey =
            key(
                "start-conflict"
            );

        mockMvc.perform(
                post(
                    "/api/v1/kitchen/tickets/{ticketId}/start",
                    first.ticketId()
                )
                    .header(
                        "Authorization",
                        bearer(
                            "kitchen.prepare"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        idempotencyKey
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                post(
                    "/api/v1/kitchen/tickets/{ticketId}/start",
                    second.ticketId()
                )
                    .header(
                        "Authorization",
                        bearer(
                            "kitchen.prepare"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        idempotencyKey
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
            ticketStatus(
                second.ticketId()
            )
        )
            .isEqualTo(
                "QUEUED"
            );

        assertThat(
            orderStatus(
                second.orderId()
            )
        )
            .isEqualTo(
                "QUEUED"
            );

        assertThat(
            transitionCount(
                second.orderId(),
                "QUEUED",
                "PREPARING"
            )
        )
            .isZero();

        assertThat(
            idempotencyCount(
                startScope(),
                idempotencyKey
            )
        )
            .isEqualTo(
                1L
            );
    }

    @Test
    void readyPromotesOrderAndOperationScopesRemainIndependent()
        throws Exception {

        TicketFixture fixture =
            insertQueuedTicket(
                organizationId
            );

        /*
         * Deliberately reuse the same key for START and READY.
         * Different operation scopes must remain independent.
         */
        String sharedKey =
            key(
                "cross-operation"
            );

        mockMvc.perform(
                post(
                    "/api/v1/kitchen/tickets/{ticketId}/start",
                    fixture.ticketId()
                )
                    .header(
                        "Authorization",
                        bearer(
                            "kitchen.prepare"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        sharedKey
                    )
            )
            .andExpect(
                status().isOk()
            );

        String firstReadyBody =
            mockMvc.perform(
                    post(
                        "/api/v1/kitchen/tickets/{ticketId}/ready",
                        fixture.ticketId()
                    )
                        .header(
                            "Authorization",
                            bearer(
                                "kitchen.ready"
                            )
                        )
                        .header(
                            "Idempotency-Key",
                            sharedKey
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        )
            .isEqualTo(
                "READY"
            );

        assertThat(
            ticketStatus(
                fixture.ticketId()
            )
        )
            .isEqualTo(
                "READY"
            );

        assertThat(
            lineStatus(
                fixture.lineId()
            )
        )
            .isEqualTo(
                "READY"
            );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "QUEUED",
                "PREPARING"
            )
        )
            .isEqualTo(
                1L
            );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "PREPARING",
                "READY"
            )
        )
            .isEqualTo(
                1L
            );

        assertThat(
            idempotencyCount(
                startScope(),
                sharedKey
            )
        )
            .isEqualTo(
                1L
            );

        assertThat(
            idempotencyCount(
                readyScope(),
                sharedKey
            )
        )
            .isEqualTo(
                1L
            );

        long readyHistoryBefore =
            transitionCount(
                fixture.orderId(),
                "PREPARING",
                "READY"
            );

        String replayBody =
            mockMvc.perform(
                    post(
                        "/api/v1/kitchen/tickets/{ticketId}/ready",
                        fixture.ticketId()
                    )
                        .header(
                            "Authorization",
                            bearer(
                                "kitchen.ready"
                            )
                        )
                        .header(
                            "Idempotency-Key",
                            sharedKey
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertSameJson(
            firstReadyBody,
            replayBody
        );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "PREPARING",
                "READY"
            )
        )
            .isEqualTo(
                readyHistoryBefore
            )
            .isEqualTo(
                1L
            );

        assertThat(
            idempotencyCount(
                readyScope(),
                sharedKey
            )
        )
            .isEqualTo(
                1L
            );
    }

    @Test
    void startRejectsMissingBlankShortAndOversizedIdempotencyKey()
        throws Exception {

        TicketFixture fixture =
            insertQueuedTicket(
                organizationId
            );

        String authorization =
            bearer(
                "kitchen.prepare"
            );

        mockMvc.perform(
                post(
                    "/api/v1/kitchen/tickets/{ticketId}/start",
                    fixture.ticketId()
                )
                    .header(
                        "Authorization",
                        authorization
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
                post(
                    "/api/v1/kitchen/tickets/{ticketId}/start",
                    fixture.ticketId()
                )
                    .header(
                        "Authorization",
                        authorization
                    )
                    .header(
                        "Idempotency-Key",
                        "   "
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
                post(
                    "/api/v1/kitchen/tickets/{ticketId}/start",
                    fixture.ticketId()
                )
                    .header(
                        "Authorization",
                        authorization
                    )
                    .header(
                        "Idempotency-Key",
                        "short"
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
                post(
                    "/api/v1/kitchen/tickets/{ticketId}/start",
                    fixture.ticketId()
                )
                    .header(
                        "Authorization",
                        authorization
                    )
                    .header(
                        "Idempotency-Key",
                        "x".repeat(161)
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
            orderStatus(
                fixture.orderId()
            )
        )
            .isEqualTo(
                "QUEUED"
            );

        assertThat(
            ticketStatus(
                fixture.ticketId()
            )
        )
            .isEqualTo(
                "QUEUED"
            );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "QUEUED",
                "PREPARING"
            )
        )
            .isZero();
    }

    @Test
    void foreignTenantTicketIsNotVisibleToStartCommand()
        throws Exception {

        UUID foreignOrganizationId =
            insertOrganization(
                "FOREIGN-KDS"
            );

        TicketFixture foreign =
            insertQueuedTicket(
                foreignOrganizationId
            );

        String idempotencyKey =
            key(
                "foreign-start"
            );

        mockMvc.perform(
                post(
                    "/api/v1/kitchen/tickets/{ticketId}/start",
                    foreign.ticketId()
                )
                    .header(
                        "Authorization",
                        bearer(
                            "kitchen.prepare"
                        )
                    )
                    .header(
                        "Idempotency-Key",
                        idempotencyKey
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "KITCHEN_TICKET_NOT_FOUND"
                    )
            );

        assertThat(
            ticketStatus(
                foreign.ticketId()
            )
        )
            .isEqualTo(
                "QUEUED"
            );

        assertThat(
            orderStatus(
                foreign.orderId()
            )
        )
            .isEqualTo(
                "QUEUED"
            );

        assertThat(
            idempotencyCount(
                startScope(),
                idempotencyKey
            )
        )
            .isZero();
    }

    @Test
    void concurrentSameKeyStartSerializesToSingleMutation()
        throws Exception {

        TicketFixture fixture =
            insertQueuedTicket(
                organizationId
            );

        String idempotencyKey =
            key(
                "concurrent-start"
            );

        String authorization =
            bearer(
                "kitchen.prepare"
            );

        CountDownLatch ready =
            new CountDownLatch(
                2
            );

        CountDownLatch fire =
            new CountDownLatch(
                1
            );

        ExecutorService executor =
            Executors.newFixedThreadPool(
                2
            );

        Callable<HttpResult> request =
            () -> {

                ready.countDown();

                boolean released =
                    fire.await(
                        20,
                        TimeUnit.SECONDS
                    );

                if (!released) {
                    throw new IllegalStateException(
                        "Concurrent start gate timed out."
                    );
                }

                MvcResult result =
                    mockMvc.perform(
                            post(
                                "/api/v1/kitchen/tickets/{ticketId}/start",
                                fixture.ticketId()
                            )
                                .header(
                                    "Authorization",
                                    authorization
                                )
                                .header(
                                    "Idempotency-Key",
                                    idempotencyKey
                                )
                        )
                        .andReturn();

                return new HttpResult(
                    result
                        .getResponse()
                        .getStatus(),
                    result
                        .getResponse()
                        .getContentAsString()
                );
            };

        Future<HttpResult> firstFuture =
            executor.submit(
                request
            );

        Future<HttpResult> secondFuture =
            executor.submit(
                request
            );

        try {

            assertThat(
                ready.await(
                    10,
                    TimeUnit.SECONDS
                )
            )
                .isTrue();

            fire.countDown();

            HttpResult first =
                firstFuture.get(
                    30,
                    TimeUnit.SECONDS
                );

            HttpResult second =
                secondFuture.get(
                    30,
                    TimeUnit.SECONDS
                );

            assertThat(
                first.status()
            )
                .isEqualTo(
                    200
                );

            assertThat(
                second.status()
            )
                .isEqualTo(
                    200
                );

            assertSameJson(
                first.body(),
                second.body()
            );

        } finally {

            fire.countDown();

            executor.shutdownNow();

            executor.awaitTermination(
                10,
                TimeUnit.SECONDS
            );
        }

        assertThat(
            idempotencyCount(
                startScope(),
                idempotencyKey
            )
        )
            .isEqualTo(
                1L
            );

        assertThat(
            validIdempotencyMetadataCount(
                startScope(),
                idempotencyKey,
                fixture.ticketId()
            )
        )
            .isEqualTo(
                1L
            );

        assertThat(
            transitionCount(
                fixture.orderId(),
                "QUEUED",
                "PREPARING"
            )
        )
            .isEqualTo(
                1L
            );

        assertThat(
            orderStatus(
                fixture.orderId()
            )
        )
            .isEqualTo(
                "PREPARING"
            );

        assertThat(
            ticketStatus(
                fixture.ticketId()
            )
        )
            .isEqualTo(
                "PREPARING"
            );

        assertThat(
            lineStatus(
                fixture.lineId()
            )
        )
            .isEqualTo(
                "PREPARING"
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
                .issuedAt(
                    now
                )
                .expiresAt(
                    now.plusSeconds(
                        600
                    )
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

    private TicketFixture insertQueuedTicket(
        UUID tenantId
    ) {

        String suffix =
            randomSuffix();

        UUID campusId =
            UUID.randomUUID();

        UUID sourceLocationId =
            UUID.randomUUID();

        UUID kitchenLocationId =
            UUID.randomUUID();

        UUID categoryId =
            UUID.randomUUID();

        UUID productId =
            UUID.randomUUID();

        UUID orderId =
            UUID.randomUUID();

        UUID orderItemId =
            UUID.randomUUID();

        UUID ticketId =
            UUID.randomUUID();

        UUID lineId =
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
            "KDS Campus " + suffix,
            "KC" + suffix
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
            sourceLocationId,
            campusId,
            "KDS Source " + suffix,
            "KS" + suffix
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
            VALUES (?, ?, ?, ?, 'KITCHEN', TRUE)
            """,
            kitchenLocationId,
            campusId,
            "KDS Kitchen " + suffix,
            "KK" + suffix
        );

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
            "KDS Category " + suffix,
            "kds-category-" + suffix
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
                FALSE,
                FALSE,
                TRUE
            )
            """,
            productId,
            tenantId,
            categoryId,
            "KDS-" + suffix,
            "KDS Product " + suffix
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
                discount_total,
                total,
                currency,
                payment_expires_at,
                paid_at,
                version,
                order_type,
                payment_status,
                tax_total,
                customer_note
            )
            VALUES (
                ?, ?, ?, ?, NULL,
                ?, CURRENT_DATE, 'POS', 'QUEUED',
                10.00, 0.00, 10.00, 'MAD',
                NULL, CURRENT_TIMESTAMP, 0,
                'POS_DIRECT', 'COMPLETED', 0.00,
                'Kitchen HTTP E2E'
            )
            """,
            orderId,
            tenantId,
            campusId,
            sourceLocationId,
            "KDS-" + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO order_items (
                id,
                order_id,
                product_id,
                variant_id,
                product_name_snapshot,
                unit_price,
                quantity,
                discount_amount,
                line_total,
                special_instructions,
                variant_name_snapshot,
                sku_snapshot,
                tax_rate_snapshot,
                line_tax
            )
            VALUES (
                ?, ?, ?, NULL,
                ?, 10.00, 1, 0.00, 10.00,
                NULL, NULL, ?, 0.00, 0.00
            )
            """,
            orderItemId,
            orderId,
            productId,
            "KDS Product " + suffix,
            "KDS-" + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO kitchen_tickets (
                id,
                order_id,
                kitchen_location_id,
                status,
                priority,
                queued_at
            )
            VALUES (
                ?, ?, ?, 'QUEUED', 0, CURRENT_TIMESTAMP
            )
            """,
            ticketId,
            orderId,
            kitchenLocationId
        );

        jdbcTemplate.update(
            """
            INSERT INTO kitchen_ticket_items (
                id,
                kitchen_ticket_id,
                order_item_id,
                menu_selection_id,
                quantity,
                status
            )
            VALUES (
                ?, ?, ?, NULL, 1.000, 'QUEUED'
            )
            """,
            lineId,
            ticketId,
            orderItemId
        );

        return new TicketFixture(
            orderId,
            ticketId,
            lineId,
            kitchenLocationId
        );
    }

    private UUID insertOrganization(
        String prefix
    ) {

        UUID id =
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
            id,
            prefix + " " + suffix,
            "FO" + suffix
        );

        return id;
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

    private String ticketStatus(
        UUID ticketId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM kitchen_tickets
            WHERE id = ?
            """,
            String.class,
            ticketId
        );
    }

    private String lineStatus(
        UUID lineId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM kitchen_ticket_items
            WHERE id = ?
            """,
            String.class,
            lineId
        );
    }

    private long startedTimestampCount(
        TicketFixture fixture
    ) {

        Long ticketCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM kitchen_tickets
                WHERE id = ?
                  AND started_at IS NOT NULL
                """,
                Long.class,
                fixture.ticketId()
            );

        Long lineCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM kitchen_ticket_items
                WHERE id = ?
                  AND started_at IS NOT NULL
                """,
                Long.class,
                fixture.lineId()
            );

        return value(
            ticketCount
        )
            + value(
                lineCount
            );
    }

    private long transitionCount(
        UUID orderId,
        String fromStatus,
        String toStatus
    ) {

        Long result =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM order_status_history
                WHERE order_id = ?
                  AND from_status = ?
                  AND to_status = ?
                """,
                Long.class,
                orderId,
                fromStatus,
                toStatus
            );

        return value(
            result
        );
    }

    private long idempotencyCount(
        String scope,
        String idempotencyKey
    ) {

        Long result =
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

        return value(
            result
        );
    }

    private long validIdempotencyMetadataCount(
        String scope,
        String idempotencyKey,
        UUID ticketId
    ) {

        Long result =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM idempotency_records
                WHERE scope = ?
                  AND idempotency_key = ?
                  AND user_id = ?
                  AND response_status = 200
                  AND response_body IS NOT NULL
                  AND resource_type = 'KITCHEN_TICKET'
                  AND resource_id = ?
                  AND request_hash IS NOT NULL
                  AND LENGTH(request_hash) = 64
                  AND expires_at > created_at
                """,
                Long.class,
                scope,
                idempotencyKey,
                userId,
                ticketId
            );

        return value(
            result
        );
    }

    private long value(
        Long value
    ) {

        return value == null
            ? 0L
            : value;
    }

    private String startScope() {

        return "KITCHEN_START:"
            + organizationId;
    }

    private String readyScope() {

        return "KITCHEN_READY:"
            + organizationId;
    }

    private String key(
        String prefix
    ) {

        return prefix
            + "-"
            + randomSuffix();
    }

    private void assertSameJson(
        String first,
        String second
    ) throws Exception {

        assertThat(
            objectMapper.readTree(
                second
            )
        )
            .isEqualTo(
                objectMapper.readTree(
                    first
                )
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

    private record TicketFixture(
        UUID orderId,
        UUID ticketId,
        UUID lineId,
        UUID kitchenLocationId
    ) {
    }

    private record HttpResult(
        int status,
        String body
    ) {
    }
}
