package com.sup2i.food.pos;

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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
class PosSessionE2EIntegrationTest {

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
    void seedActor() {

        String suffix =
            randomSuffix();

        organizationId =
            insertOrganization(
                "POS" + suffix
            );

        userId =
            UUID.randomUUID();

        email =
            "pos-session-"
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
            organizationId,
            email,
            "POS",
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
                "pos-session-e2e",
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
        )
            .isNotBlank();
    }

    @Test
    void openCreatesOpenSession()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        String idempotencyKey =
            key(
                "open-create"
            );

        performOpen(
            terminalId,
            "100.00",
            idempotencyKey,
            "pos.open"
        )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.terminalId")
                    .value(
                        terminalId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.cashierId")
                    .value(
                        userId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.status")
                    .value(
                        "OPEN"
                    )
            )
            .andExpect(
                jsonPath("$.openingCash")
                    .value(
                        100.00
                    )
            );

        assertThat(
            openSessionCount(
                terminalId
            )
        )
            .isEqualTo(
                1L
            );

        assertThat(
            idempotencyCount(
                openScope(),
                idempotencyKey
            )
        )
            .isEqualTo(
                1L
            );
    }

    @Test
    void openRequiresPosOpenPermission()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        performOpen(
            terminalId,
            "10.00",
            key(
                "open-rbac"
            )
        )
            .andExpect(
                status().isForbidden()
            );

        assertThat(
            openSessionCount(
                terminalId
            )
        )
            .isZero();
    }

    @Test
    void openRequiresIdempotencyHeader()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        mockMvc.perform(
                post(
                    "/api/v1/pos/sessions"
                )
                    .header(
                        "Authorization",
                        bearer(
                            "pos.open"
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        openBody(
                            terminalId,
                            "10.00"
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
            openSessionCount(
                terminalId
            )
        )
            .isZero();
    }

    @Test
    void openRejectsShortIdempotencyKey()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        performOpen(
            terminalId,
            "10.00",
            "short",
            "pos.open"
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
    void openSameKeyReplaysStableSnapshot()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        String idempotencyKey =
            key(
                "open-replay"
            );

        MvcResult first =
            performOpen(
                terminalId,
                "25.50",
                idempotencyKey,
                "pos.open"
            )
                .andExpect(
                    status().isCreated()
                )
                .andReturn();

        MvcResult replay =
            performOpen(
                terminalId,
                "25.50",
                idempotencyKey,
                "pos.open"
            )
                .andExpect(
                    status().isCreated()
                )
                .andReturn();

        assertThat(
            replay
                .getResponse()
                .getContentAsString()
        )
            .isEqualTo(
                first
                    .getResponse()
                    .getContentAsString()
            );

        assertThat(
            openSessionCount(
                terminalId
            )
        )
            .isEqualTo(
                1L
            );
    }

    @Test
    void openReplayAfterCloseStillReturnsOriginalOpenSnapshot()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        String openKey =
            key(
                "open-after-close"
            );

        MvcResult firstOpen =
            performOpen(
                terminalId,
                "30.00",
                openKey,
                "pos.open"
            )
                .andExpect(
                    status().isCreated()
                )
                .andReturn();

        UUID sessionId =
            openSessionId(
                terminalId
            );

        performClose(
            sessionId,
            "30.00",
            null,
            key(
                "close-open-replay"
            ),
            "pos.close"
        )
            .andExpect(
                status().isOk()
            );

        MvcResult replay =
            performOpen(
                terminalId,
                "30.00",
                openKey,
                "pos.open"
            )
                .andExpect(
                    status().isCreated()
                )
                .andExpect(
                    jsonPath("$.status")
                        .value(
                            "OPEN"
                        )
                )
                .andReturn();

        assertThat(
            replay
                .getResponse()
                .getContentAsString()
        )
            .isEqualTo(
                firstOpen
                    .getResponse()
                    .getContentAsString()
            );

        assertThat(
            sessionStatus(
                sessionId
            )
        )
            .isEqualTo(
                "CLOSED"
            );
    }

    @Test
    void openSameKeyDifferentPayloadReturnsIdempotencyConflict()
        throws Exception {

        UUID firstTerminal =
            insertTerminal(
                organizationId,
                true
            );

        UUID secondTerminal =
            insertTerminal(
                organizationId,
                true
            );

        String idempotencyKey =
            key(
                "open-conflict"
            );

        performOpen(
            firstTerminal,
            "10.00",
            idempotencyKey,
            "pos.open"
        )
            .andExpect(
                status().isCreated()
            );

        performOpen(
            secondTerminal,
            "10.00",
            idempotencyKey,
            "pos.open"
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
            openSessionCount(
                secondTerminal
            )
        )
            .isZero();
    }

    @Test
    void openDifferentKeyOnAlreadyOpenTerminalReturnsTypedConflict()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        performOpen(
            terminalId,
            "10.00",
            key(
                "open-one"
            ),
            "pos.open"
        )
            .andExpect(
                status().isCreated()
            );

        performOpen(
            terminalId,
            "20.00",
            key(
                "open-two"
            ),
            "pos.open"
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "POS_SESSION_ALREADY_OPEN"
                    )
            );

        assertThat(
            openSessionCount(
                terminalId
            )
        )
            .isEqualTo(
                1L
            );
    }

    @Test
    void openRejectsForeignTenantTerminal()
        throws Exception {

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN"
                    + randomSuffix()
            );

        UUID terminalId =
            insertTerminal(
                foreignOrganization,
                true
            );

        performOpen(
            terminalId,
            "10.00",
            key(
                "open-foreign"
            ),
            "pos.open"
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
            openSessionCount(
                terminalId
            )
        )
            .isZero();
    }

    @Test
    void concurrentSameKeyOpenReturnsTwoStableSuccessesAndOneMutation()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        String idempotencyKey =
            key(
                "open-same-race"
            );

        String authorization =
            bearer(
                "pos.open"
            );

        CountDownLatch ready =
            new CountDownLatch(
                2
            );

        CountDownLatch start =
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
                    start.await(
                        15,
                        TimeUnit.SECONDS
                    );

                if (!released) {
                    throw new IllegalStateException(
                        "Concurrent start latch timed out."
                    );
                }

                MvcResult result =
                    mockMvc.perform(
                        post(
                            "/api/v1/pos/sessions"
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
                            .content(
                                openBody(
                                    terminalId,
                                    "15.00"
                                )
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

        try {

            Future<HttpResult> first =
                executor.submit(
                    request
                );

            Future<HttpResult> second =
                executor.submit(
                    request
                );

            assertThat(
                ready.await(
                    15,
                    TimeUnit.SECONDS
                )
            )
                .isTrue();

            start.countDown();

            HttpResult firstResult =
                first.get(
                    60,
                    TimeUnit.SECONDS
                );

            HttpResult secondResult =
                second.get(
                    60,
                    TimeUnit.SECONDS
                );

            assertThat(
                List.of(
                    firstResult.status(),
                    secondResult.status()
                )
            )
                .containsExactlyInAnyOrder(
                    201,
                    201
                );

            assertThat(
                secondResult.body()
            )
                .isEqualTo(
                    firstResult.body()
                );

        } finally {

            executor.shutdownNow();
        }

        assertThat(
            openSessionCount(
                terminalId
            )
        )
            .isEqualTo(
                1L
            );

        assertThat(
            idempotencyCount(
                openScope(),
                idempotencyKey
            )
        )
            .isEqualTo(
                1L
            );
    }

    @Test
    void concurrentDifferentKeysCannotCreateTwoOpenSessions()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        String authorization =
            bearer(
                "pos.open"
            );

        CountDownLatch ready =
            new CountDownLatch(
                2
            );

        CountDownLatch start =
            new CountDownLatch(
                1
            );

        ExecutorService executor =
            Executors.newFixedThreadPool(
                2
            );

        List<String> keys =
            List.of(
                key(
                    "open-race-a"
                ),
                key(
                    "open-race-b"
                )
            );

        List<Future<Integer>> futures =
            new ArrayList<>();

        try {

            for (String currentKey : keys) {

                futures.add(
                    executor.submit(
                        () -> {

                            ready.countDown();

                            boolean released =
                                start.await(
                                    15,
                                    TimeUnit.SECONDS
                                );

                            if (!released) {
                                throw new IllegalStateException(
                                    "Concurrent start latch timed out."
                                );
                            }

                            MvcResult result =
                                mockMvc.perform(
                                    post(
                                        "/api/v1/pos/sessions"
                                    )
                                        .header(
                                            "Authorization",
                                            authorization
                                        )
                                        .header(
                                            "Idempotency-Key",
                                            currentKey
                                        )
                                        .contentType(
                                            MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                            openBody(
                                                terminalId,
                                                "10.00"
                                            )
                                        )
                                )
                                    .andReturn();

                            return result
                                .getResponse()
                                .getStatus();
                        }
                    )
                );
            }

            assertThat(
                ready.await(
                    15,
                    TimeUnit.SECONDS
                )
            )
                .isTrue();

            start.countDown();

            List<Integer> statuses =
                new ArrayList<>();

            for (Future<Integer> future : futures) {

                statuses.add(
                    future.get(
                        60,
                        TimeUnit.SECONDS
                    )
                );
            }

            assertThat(
                statuses
            )
                .containsExactlyInAnyOrder(
                    201,
                    409
                );

        } finally {

            executor.shutdownNow();
        }

        assertThat(
            openSessionCount(
                terminalId
            )
        )
            .isEqualTo(
                1L
            );
    }

    @Test
    void closeTransitionsOwnedOpenSessionToClosed()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        performOpen(
            terminalId,
            "100.00",
            key(
                "close-create"
            ),
            "pos.open"
        )
            .andExpect(
                status().isCreated()
            );

        UUID sessionId =
            openSessionId(
                terminalId
            );

        String closeKey =
            key(
                "close-success"
            );

        performClose(
            sessionId,
            "125.50",
            "Normal closing",
            closeKey,
            "pos.close"
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.id")
                    .value(
                        sessionId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.status")
                    .value(
                        "CLOSED"
                    )
            )
            .andExpect(
                jsonPath("$.countedCash")
                    .value(
                        125.50
                    )
            );

        assertThat(
            sessionStatus(
                sessionId
            )
        )
            .isEqualTo(
                "CLOSED"
            );

        assertThat(
            closeReason(
                sessionId
            )
        )
            .isEqualTo(
                "Normal closing"
            );

        assertThat(
            idempotencyCount(
                closeScope(),
                closeKey
            )
        )
            .isEqualTo(
                1L
            );
    }

    @Test
    void closeRequiresPosClosePermission()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        performOpen(
            terminalId,
            "10.00",
            key(
                "close-rbac-open"
            ),
            "pos.open"
        )
            .andExpect(
                status().isCreated()
            );

        UUID sessionId =
            openSessionId(
                terminalId
            );

        performClose(
            sessionId,
            "10.00",
            null,
            key(
                "close-rbac"
            ),
            "pos.open"
        )
            .andExpect(
                status().isForbidden()
            );

        assertThat(
            sessionStatus(
                sessionId
            )
        )
            .isEqualTo(
                "OPEN"
            );
    }

    @Test
    void closeRequiresIdempotencyHeader()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        performOpen(
            terminalId,
            "10.00",
            key(
                "close-header-open"
            ),
            "pos.open"
        )
            .andExpect(
                status().isCreated()
            );

        UUID sessionId =
            openSessionId(
                terminalId
            );

        mockMvc.perform(
                post(
                    "/api/v1/pos/sessions/{sessionId}/close",
                    sessionId
                )
                    .header(
                        "Authorization",
                        bearer(
                            "pos.close"
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        closeBody(
                            "10.00",
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
            sessionStatus(
                sessionId
            )
        )
            .isEqualTo(
                "OPEN"
            );
    }

    @Test
    void closeSameKeyReplaysStableSnapshot()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        performOpen(
            terminalId,
            "10.00",
            key(
                "close-replay-open"
            ),
            "pos.open"
        )
            .andExpect(
                status().isCreated()
            );

        UUID sessionId =
            openSessionId(
                terminalId
            );

        String closeKey =
            key(
                "close-replay"
            );

        MvcResult first =
            performClose(
                sessionId,
                "11.00",
                "Counted",
                closeKey,
                "pos.close"
            )
                .andExpect(
                    status().isOk()
                )
                .andReturn();

        MvcResult replay =
            performClose(
                sessionId,
                "11.00",
                "Counted",
                closeKey,
                "pos.close"
            )
                .andExpect(
                    status().isOk()
                )
                .andReturn();

        assertThat(
            replay
                .getResponse()
                .getContentAsString()
        )
            .isEqualTo(
                first
                    .getResponse()
                    .getContentAsString()
            );

        assertThat(
            idempotencyCount(
                closeScope(),
                closeKey
            )
        )
            .isEqualTo(
                1L
            );
    }

    @Test
    void closeSameKeyDifferentPayloadReturnsIdempotencyConflict()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        performOpen(
            terminalId,
            "10.00",
            key(
                "close-conflict-open"
            ),
            "pos.open"
        )
            .andExpect(
                status().isCreated()
            );

        UUID sessionId =
            openSessionId(
                terminalId
            );

        String closeKey =
            key(
                "close-conflict"
            );

        performClose(
            sessionId,
            "10.00",
            null,
            closeKey,
            "pos.close"
        )
            .andExpect(
                status().isOk()
            );

        performClose(
            sessionId,
            "20.00",
            null,
            closeKey,
            "pos.close"
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
    }

    @Test
    void closeDifferentKeyAfterClosedReturnsSessionNotOpen()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        performOpen(
            terminalId,
            "10.00",
            key(
                "close-twice-open"
            ),
            "pos.open"
        )
            .andExpect(
                status().isCreated()
            );

        UUID sessionId =
            openSessionId(
                terminalId
            );

        performClose(
            sessionId,
            "10.00",
            null,
            key(
                "close-one"
            ),
            "pos.close"
        )
            .andExpect(
                status().isOk()
            );

        performClose(
            sessionId,
            "10.00",
            null,
            key(
                "close-two"
            ),
            "pos.close"
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
    }

    @Test
    void closeDoesNotAllowAnotherCashiersSession()
        throws Exception {

        UUID terminalId =
            insertTerminal(
                organizationId,
                true
            );

        UUID otherCashier =
            insertOtherCashier();

        UUID sessionId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO pos_sessions (
                id,
                terminal_id,
                cashier_id,
                opening_cash,
                status
            )
            VALUES (?, ?, ?, 0.00, 'OPEN')
            """,
            sessionId,
            terminalId,
            otherCashier
        );

        performClose(
            sessionId,
            "0.00",
            null,
            key(
                "close-other"
            ),
            "pos.close"
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
            sessionStatus(
                sessionId
            )
        )
            .isEqualTo(
                "OPEN"
            );
    }

    private ResultActions performOpen(
        UUID terminalId,
        String openingCash,
        String idempotencyKey,
        String... permissions
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/pos/sessions"
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
                    openBody(
                        terminalId,
                        openingCash
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
                "/api/v1/pos/sessions/{sessionId}/close",
                sessionId
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

    private UUID insertTerminal(
        UUID tenantId,
        boolean active
    ) {

        String suffix =
            randomSuffix();

        UUID campusId =
            UUID.randomUUID();

        UUID locationId =
            UUID.randomUUID();

        UUID terminalId =
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
            "POS Campus " + suffix,
            "PC" + suffix
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
            "POS Location " + suffix,
            "PL" + suffix
        );

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
            VALUES (?, ?, ?, ?, 'SUP2I_POS', ?, 'POS')
            """,
            terminalId,
            locationId,
            "PT" + suffix,
            "POS Terminal " + suffix,
            active
        );

        return terminalId;
    }

    private UUID insertOtherCashier() {

        UUID id =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

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
            id,
            organizationId,
            "other-pos-"
                + suffix
                + "@sup2i.test",
            "Other",
            "Cashier"
        );

        return id;
    }

    private long openSessionCount(
        UUID terminalId
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pos_sessions
                WHERE terminal_id = ?
                  AND status = 'OPEN'
                """,
                Long.class,
                terminalId
            );

        return count == null
            ? 0L
            : count;
    }

    private UUID openSessionId(
        UUID terminalId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM pos_sessions
            WHERE terminal_id = ?
              AND status = 'OPEN'
            """,
            UUID.class,
            terminalId
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

    private String closeReason(
        UUID sessionId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT close_reason
            FROM pos_sessions
            WHERE id = ?
            """,
            String.class,
            sessionId
        );
    }

    private long idempotencyCount(
        String scope,
        String idempotencyKey
    ) {

        Long count =
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

        return count == null
            ? 0L
            : count;
    }

    private String openScope() {

        return "POS_SESSION_OPEN:"
            + organizationId;
    }

    private String closeScope() {

        return "POS_SESSION_CLOSE:"
            + organizationId;
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

    private String openBody(
        UUID terminalId,
        String openingCash
    ) {

        return """
            {
              "terminalId": "%s",
              "openingCash": %s
            }
            """
            .formatted(
                terminalId,
                openingCash
            );
    }

    private String closeBody(
        String countedCash,
        String comment
    ) {

        if (comment == null) {

            return """
                {
                  "countedCash": %s,
                  "comment": null
                }
                """
                .formatted(
                    countedCash
                );
        }

        return """
            {
              "countedCash": %s,
              "comment": "%s"
            }
            """
            .formatted(
                countedCash,
                comment
            );
    }

    private String key(
        String prefix
    ) {

        return prefix
            + "-"
            + randomSuffix();
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

    private record HttpResult(
        int status,
        String body
    ) {
    }
}