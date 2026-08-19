package com.sup2i.food.security;

import com.sup2i.food.security.api.dto.AuthResponse;
import com.sup2i.food.security.api.dto.MfaTotpSetupResponse;
import com.sup2i.food.security.service.Base32Codec;
import com.sup2i.food.security.service.TokenHashService;
import com.sup2i.food.security.service.TotpService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import com.sup2i.food.security.api.dto.MfaTotpConfirmResponse;
import com.sup2i.food.security.api.dto.MfaTotpSetupResponse;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(
    properties = {
        "sup2i.security.local-provider-code=local",
        "sup2i.security.jwt.issuer=sup2i-food-backend-test",
        "sup2i.security.jwt.access-token-ttl=15m",

        // TEST ONLY:
        // "0123456789abcdef0123456789abcdef" encodé Base64.
        "sup2i.security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",

        "sup2i.security.refresh-token-ttl=30d",
        "sup2i.security.login-protection.enabled=true",
        "sup2i.security.login-protection.max-failed-attempts=3",
        "sup2i.security.login-protection.failure-window=15m",
        "sup2i.security.mfa.enabled=true",
        "sup2i.security.mfa.required-roles=ADMINISTRATION,SYSTEM_ADMIN",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.recovery-code-count=10"
    }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthE2EIntegrationTest {

    private static final String EMAIL =
        "e2e.student@sup2i.test";

    private static final String PASSWORD =
        "Sup2iE2E!2026";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName("sup2i_food_test")
            .withUsername("sup2i_food_test")
            .withPassword("sup2i_food_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TotpService totpService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenHashService tokenHashService;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void seedDatabase() {

        /*
         * DB totalement isolée par Testcontainers.
         * On peut donc nettoyer le jeu E2E avant chaque test.
         */
        jdbcTemplate.execute(
            "TRUNCATE TABLE organizations, roles CASCADE"
        );

        UUID organizationId =
            jdbcTemplate.queryForObject(
                """
                INSERT INTO organizations (
                    name,
                    code,
                    is_active
                )
                VALUES (
                    'SUP2I Test',
                    'SUP2I-TEST',
                    true
                )
                RETURNING id
                """,
                UUID.class
            );

        UUID campusId =
            jdbcTemplate.queryForObject(
                """
                INSERT INTO campuses (
                    organization_id,
                    name,
                    code,
                    timezone,
                    is_active
                )
                VALUES (
                    ?,
                    'Campus Test',
                    'TEST-CAMPUS',
                    'Africa/Casablanca',
                    true
                )
                RETURNING id
                """,
                UUID.class,
                organizationId
            );

        UUID roleId =
            jdbcTemplate.queryForObject(
                """
                INSERT INTO roles (
                    code,
                    name,
                    description,
                    is_system
                )
                VALUES (
                    'STUDENT',
                    'Student',
                    'Student integration test role',
                    true
                )
                RETURNING id
                """,
                UUID.class
            );

        UUID userId =
            jdbcTemplate.queryForObject(
                """
                INSERT INTO users (
                    organization_id,
                    email,
                    first_name,
                    last_name,
                    status
                )
                VALUES (
                    ?,
                    ?,
                    'E2E',
                    'Student',
                    'ACTIVE'
                )
                RETURNING id
                """,
                UUID.class,
                organizationId,
                EMAIL
            );

        jdbcTemplate.update(
            """
            INSERT INTO students (
                user_id,
                campus_id,
                student_number,
                program,
                level,
                group_name,
                enrollment_status
            )
            VALUES (
                ?,
                ?,
                'E2E-0001',
                'E2E',
                'TEST',
                'E2E',
                'ACTIVE'
            )
            """,
            userId,
            campusId
        );

        jdbcTemplate.update(
            """
            INSERT INTO auth_identities (
                user_id,
                provider_type,
                provider_code,
                login_identifier,
                password_hash,
                is_verified,
                is_primary,
                is_active
            )
            VALUES (
                ?,
                'LOCAL',
                'local',
                ?,
                ?,
                true,
                true,
                true
            )
            """,
            userId,
            EMAIL,
            passwordEncoder.encode(PASSWORD)
        );

        jdbcTemplate.update(
            """
            INSERT INTO user_roles (
                user_id,
                role_id,
                campus_id,
                location_id,
                assigned_by
            )
            VALUES (
                ?,
                ?,
                NULL,
                NULL,
                NULL
            )
            """,
            userId,
            roleId
        );
    }

    @Test
    void loginAndMeWork() throws Exception {

        AuthResponse auth =
            login(PASSWORD);

        assertThat(auth.accessToken())
            .isNotBlank();

        assertThat(auth.refreshToken())
            .isNotBlank();

        assertThat(auth.expiresIn())
            .isPositive();

        assertThat(auth.user().email())
            .isEqualTo(EMAIL);

        assertThat(auth.user().roles())
            .contains("STUDENT");

        mockMvc.perform(
                get("/api/v1/me")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(auth.accessToken())
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.email")
                    .value(EMAIL)
            )
            .andExpect(
                jsonPath("$.status")
                    .value("ACTIVE")
            )
            .andExpect(
                jsonPath("$.student.studentNumber")
                    .value("E2E-0001")
            );
    }

    @Test
    void refreshRotatesToken() throws Exception {

        AuthResponse first =
            login(PASSWORD);

        AuthResponse second =
            refresh(
                first.refreshToken()
            );

        assertThat(second.accessToken())
            .isNotEqualTo(first.accessToken());

        assertThat(second.refreshToken())
            .isNotEqualTo(first.refreshToken());

        String oldHash =
            tokenHashService.hash(
                first.refreshToken()
            );

        Map<String, Object> oldToken =
            jdbcTemplate.queryForMap(
                """
                SELECT
                    revoked_at,
                    replaced_by_id
                FROM refresh_tokens
                WHERE token_hash = ?
                """,
                oldHash
            );

        assertThat(
            oldToken.get("revoked_at")
        ).isNotNull();

        assertThat(
            oldToken.get("replaced_by_id")
        ).isNotNull();
    }

    @Test
    void refreshReuseRevokesReplacement()
        throws Exception {

        AuthResponse first =
            login(PASSWORD);

        AuthResponse second =
            refresh(
                first.refreshToken()
            );

        mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "refreshToken",
                                first.refreshToken()
                            )
                        )
                    )
            )
            .andExpect(
                status().isUnauthorized()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("UNAUTHORIZED")
            );

        /*
         * Le token de remplacement doit maintenant
         * lui aussi être inutilisable.
         */
        mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "refreshToken",
                                second.refreshToken()
                            )
                        )
                    )
            )
            .andExpect(
                status().isUnauthorized()
            );

        Long activeSessions =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_tokens rt
                JOIN users u
                  ON u.id = rt.user_id
                WHERE u.email = ?
                  AND rt.revoked_at IS NULL
                """,
                Long.class,
                EMAIL
            );

        assertThat(activeSessions)
            .isZero();
    }

    @Test
    void recoveryCodeCanBeUsedOnlyOnce()
        throws Exception {

        grantRole("SYSTEM_ADMIN");

        /*
        * 1. Démarrage enrollment TOTP.
        */
        MfaTotpSetupResponse setup =
            setupTotp();

        byte[] secret =
            Base32Codec.decode(
                setup.secret()
            );

        String confirmationCode =
            totpService.currentCode(
                secret
            );

        /*
        * 2. Activation MFA + récupération
        *    des recovery codes.
        */
        MvcResult confirmationResult =
            mockMvc.perform(
                    post(
                        "/api/v1/auth/mfa/totp/confirm"
                    )
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            json(
                                Map.of(
                                    "email",
                                    EMAIL,
                                    "password",
                                    PASSWORD,
                                    "methodId",
                                    setup
                                        .methodId()
                                        .toString(),
                                    "code",
                                    confirmationCode
                                )
                            )
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn();

        MfaTotpConfirmResponse confirmation =
            jsonMapper.readValue(
                confirmationResult
                    .getResponse()
                    .getContentAsString(),
                MfaTotpConfirmResponse.class
            );

        assertThat(
            confirmation.recoveryCodes()
        )
            .hasSize(10);

        String recoveryCode =
            confirmation
                .recoveryCodes()
                .getFirst();

        assertThat(recoveryCode)
            .isNotBlank();

        /*
        * 3. Première utilisation :
        *    doit authentifier.
        */
        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "email",
                                EMAIL,
                                "password",
                                PASSWORD,
                                "recoveryCode",
                                recoveryCode
                            )
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.accessToken")
                    .isNotEmpty()
            )
            .andExpect(
                jsonPath("$.refreshToken")
                    .isNotEmpty()
            );

        /*
        * 4. La DB doit maintenant marquer
        *    exactement un recovery code utilisé.
        */
        Long usedAfterFirstAttempt =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_mfa_recovery_codes
                WHERE used_at IS NOT NULL
                """,
                Long.class
            );

        assertThat(
            usedAfterFirstAttempt
        )
            .isEqualTo(1L);

        /*
        * 5. Deuxième utilisation du même code :
        *    interdite.
        */
        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "email",
                                EMAIL,
                                "password",
                                PASSWORD,
                                "recoveryCode",
                                recoveryCode
                            )
                        )
                    )
            )
            .andExpect(
                status().isUnauthorized()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("UNAUTHORIZED")
            );

        /*
        * 6. Toujours exactement un code consommé.
        *    Aucun second usage n'a été accepté.
        */
        Long usedAfterReplay =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM user_mfa_recovery_codes
                WHERE used_at IS NOT NULL
                """,
                Long.class
            );

        assertThat(
            usedAfterReplay
        )
            .isEqualTo(1L);
    }

    @Test
    void logoutImmediatelyInvalidatesAccessAndRefresh()
        throws Exception {

        AuthResponse auth =
            login(PASSWORD);

        /*
         * JWT valide avant logout.
         */
        mockMvc.perform(
                get("/api/v1/me")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(auth.accessToken())
                    )
            )
            .andExpect(
                status().isOk()
            );

        /*
         * Logout.
         */
        mockMvc.perform(
                post("/api/v1/auth/logout")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(auth.accessToken())
                    )
            )
            .andExpect(
                status().isNoContent()
            );

        /*
         * Le JWT déjà signé doit être rejeté
         * immédiatement grâce au sid.
         */
        mockMvc.perform(
                get("/api/v1/me")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(auth.accessToken())
                    )
            )
            .andExpect(
                status().isUnauthorized()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("UNAUTHORIZED")
            );

        /*
         * Le refresh de la même session
         * doit également être rejeté.
         */
        mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "refreshToken",
                                auth.refreshToken()
                            )
                        )
                    )
            )
            .andExpect(
                status().isUnauthorized()
            );

        String hash =
            tokenHashService.hash(
                auth.refreshToken()
            );

        Integer revoked =
            jdbcTemplate.queryForObject(
                """
                SELECT
                    CASE
                        WHEN revoked_at IS NULL
                        THEN 0
                        ELSE 1
                    END
                FROM refresh_tokens
                WHERE token_hash = ?
                """,
                Integer.class,
                hash
            );

        assertThat(revoked)
            .isEqualTo(1);
    }

    @Test
void blockedAccountReturnsAccountBlocked()
    throws Exception {

    jdbcTemplate.update(
        """
        UPDATE users
        SET status = 'BLOCKED'
        WHERE email = ?
        """,
        EMAIL
    );

    mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    json(
                        Map.of(
                            "email",
                            EMAIL,
                            "password",
                            PASSWORD
                        )
                    )
                )
        )
        .andExpect(
            status().isForbidden()
        )
        .andExpect(
            jsonPath("$.code")
                .value("ACCOUNT_BLOCKED")
        );

    Long auditCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auth_login_events ale
            JOIN users u
              ON u.id = ale.user_id
            WHERE u.email = ?
              AND ale.result = 'BLOCKED'
              AND ale.failure_reason =
                  'ACCOUNT_BLOCKED'
            """,
            Long.class,
            EMAIL
        );

    assertThat(auditCount)
        .isEqualTo(1L);
}

@Test
void suspendedAccountReturnsAccountSuspended()
    throws Exception {

    jdbcTemplate.update(
        """
        UPDATE users
        SET status = 'SUSPENDED'
        WHERE email = ?
        """,
        EMAIL
    );

    mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    json(
                        Map.of(
                            "email",
                            EMAIL,
                            "password",
                            PASSWORD
                        )
                    )
                )
        )
        .andExpect(
            status().isForbidden()
        )
        .andExpect(
            jsonPath("$.code")
                .value("ACCOUNT_SUSPENDED")
        );

    Long auditCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auth_login_events ale
            JOIN users u
              ON u.id = ale.user_id
            WHERE u.email = ?
              AND ale.result = 'BLOCKED'
              AND ale.failure_reason =
                  'ACCOUNT_SUSPENDED'
            """,
            Long.class,
            EMAIL
        );

    assertThat(auditCount)
        .isEqualTo(1L);
}

@Test
void tooManyFailedLoginsAreRateLimited()
    throws Exception {

    String wrongPassword =
        "WrongPassword!2026";

    /*
     * Seuil TEST = 3.
     */
    for (int attempt = 0;
         attempt < 3;
         attempt++) {

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "email",
                                EMAIL,
                                "password",
                                wrongPassword
                            )
                        )
                    )
            )
            .andExpect(
                status().isUnauthorized()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("UNAUTHORIZED")
            );
    }

    /*
     * 4e tentative pendant la fenêtre :
     * le password n'est même plus évalué.
     */
    mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    json(
                        Map.of(
                            "email",
                            EMAIL,
                            "password",
                            wrongPassword
                        )
                    )
                )
        )
        .andExpect(
            status().isTooManyRequests()
        )
        .andExpect(
            jsonPath("$.code")
                .value("RATE_LIMITED")
        );

    Long failed =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auth_login_events ale
            JOIN users u
              ON u.id = ale.user_id
            WHERE u.email = ?
              AND ale.result = 'FAILED'
              AND ale.failure_reason =
                  'INVALID_CREDENTIALS'
            """,
            Long.class,
            EMAIL
        );

    assertThat(failed)
        .isEqualTo(3L);

    Long throttled =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auth_login_events ale
            JOIN users u
              ON u.id = ale.user_id
            WHERE u.email = ?
              AND ale.result = 'BLOCKED'
              AND ale.failure_reason =
                  'LOGIN_RATE_LIMITED'
            """,
            Long.class,
            EMAIL
        );

    assertThat(throttled)
        .isEqualTo(1L);
}

    @Test
    void invalidPasswordIsAudited()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "email",
                                EMAIL,
                                "password",
                                "WrongPassword!2026"
                            )
                        )
                    )
            )
            .andExpect(
                status().isUnauthorized()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("UNAUTHORIZED")
            );

        Long failures =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM auth_login_events ale
                JOIN users u
                  ON u.id = ale.user_id
                WHERE u.email = ?
                  AND ale.result = 'FAILED'
                """,
                Long.class,
                EMAIL
            );

        assertThat(failures)
            .isEqualTo(1L);
    }

    private AuthResponse login(
        String password
    ) throws Exception {

        MvcResult result =
            mockMvc.perform(
                    post("/api/v1/auth/login")
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            json(
                                Map.of(
                                    "email",
                                    EMAIL,
                                    "password",
                                    password
                                )
                            )
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn();

        return jsonMapper.readValue(
            result.getResponse()
                .getContentAsString(),
            AuthResponse.class
        );
    }

    private AuthResponse refresh(
        String refreshToken
    ) throws Exception {

        MvcResult result =
            mockMvc.perform(
                    post("/api/v1/auth/refresh")
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            json(
                                Map.of(
                                    "refreshToken",
                                    refreshToken
                                )
                            )
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn();

        return jsonMapper.readValue(
            result.getResponse()
                .getContentAsString(),
            AuthResponse.class
        );
    }

    private String json(
        Object value
    ) throws Exception {

        return jsonMapper
            .writeValueAsString(value);
    }

    private String bearer(
        String accessToken
    ) {
        return "Bearer " + accessToken;
    }

    private void grantRole(
        String roleCode
    ) {
        UUID userId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM users
                WHERE email = ?
                """,
                UUID.class,
                EMAIL
            );

        UUID roleId =
            jdbcTemplate.queryForObject(
                """
                INSERT INTO roles (
                    code,
                    name,
                    description,
                    is_system
                )
                VALUES (
                    ?,
                    ?,
                    'MFA E2E role',
                    true
                )
                ON CONFLICT (code)
                DO UPDATE
                SET name = EXCLUDED.name
                RETURNING id
                """,
                UUID.class,
                roleCode,
                roleCode
            );

        jdbcTemplate.update(
            """
            INSERT INTO user_roles (
                user_id,
                role_id,
                campus_id,
                location_id,
                assigned_by
            )
            VALUES (
                ?,
                ?,
                NULL,
                NULL,
                NULL
            )
            ON CONFLICT DO NOTHING
            """,
            userId,
            roleId
        );
    }

    private MfaTotpSetupResponse setupTotp()
    throws Exception {

    MvcResult result =
        mockMvc.perform(
                post(
                    "/api/v1/auth/mfa/totp/setup"
                )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "email",
                                EMAIL,
                                "password",
                                PASSWORD,
                                "label",
                                "E2E Authenticator"
                            )
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andReturn();

    return jsonMapper.readValue(
        result.getResponse()
            .getContentAsString(),
        MfaTotpSetupResponse.class
    );
}

@Test
void sensitiveAccountRequiresMfaSetup()
    throws Exception {

    grantRole("ADMINISTRATION");

    mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    json(
                        Map.of(
                            "email",
                            EMAIL,
                            "password",
                            PASSWORD
                        )
                    )
                )
        )
        .andExpect(
            status().isForbidden()
        )
        .andExpect(
            jsonPath("$.code")
                .value(
                    "MFA_SETUP_REQUIRED"
                )
        );

    Long sessions =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM refresh_tokens
            """,
            Long.class
        );

    assertThat(sessions)
        .isZero();
}

@Test
void totpSetupActivatesMfaAndReturnsRecoveryCodes()
    throws Exception {

    grantRole("SYSTEM_ADMIN");

    MfaTotpSetupResponse setup =
        setupTotp();

    assertThat(setup.secret())
        .isNotBlank();

    assertThat(setup.otpauthUri())
        .startsWith(
            "otpauth://totp/"
        );

    byte[] secret =
        Base32Codec.decode(
            setup.secret()
        );

    String code =
        totpService.currentCode(
            secret
        );

    MvcResult confirmation =
        mockMvc.perform(
                post(
                    "/api/v1/auth/mfa/totp/confirm"
                )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "email",
                                EMAIL,
                                "password",
                                PASSWORD,
                                "methodId",
                                setup
                                    .methodId()
                                    .toString(),
                                "code",
                                code
                            )
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath(
                    "$.auth.accessToken"
                ).isNotEmpty()
            )
            .andExpect(
                jsonPath(
                    "$.recoveryCodes.length()"
                ).value(10)
            )
            .andReturn();

    String response =
        confirmation
            .getResponse()
            .getContentAsString();

    assertThat(response)
        .doesNotContain(
            setup.secret()
        );

    byte[] stored =
        jdbcTemplate.queryForObject(
            """
            SELECT secret_ciphertext
            FROM user_mfa_methods
            WHERE id = ?
            """,
            byte[].class,
            setup.methodId()
        );

    assertThat(stored)
        .isNotEqualTo(secret);
}

@Test
void configuredSensitiveAccountRequiresSecondFactor()
    throws Exception {

    grantRole("ADMINISTRATION");

    MfaTotpSetupResponse setup =
        setupTotp();

    byte[] secret =
        Base32Codec.decode(
            setup.secret()
        );

    String confirmationCode =
        totpService.currentCode(
            secret
        );

    mockMvc.perform(
            post(
                "/api/v1/auth/mfa/totp/confirm"
            )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    json(
                        Map.of(
                            "email",
                            EMAIL,
                            "password",
                            PASSWORD,
                            "methodId",
                            setup.methodId()
                                .toString(),
                            "code",
                            confirmationCode
                        )
                    )
                )
        )
        .andExpect(
            status().isOk()
        );

    mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    json(
                        Map.of(
                            "email",
                            EMAIL,
                            "password",
                            PASSWORD
                        )
                    )
                )
        )
        .andExpect(
            status().isUnauthorized()
        )
        .andExpect(
            jsonPath("$.code")
                .value("MFA_REQUIRED")
        );

    /*
     * +1 fenêtre TOTP :
     * notre vérificateur RFC6238 accepte
     * une petite tolérance d'horloge et
     * empêche la réutilisation du code
     * utilisé pour l'enrollment.
     */
    String nextCode =
        totpService.codeFor(
            secret,
            Instant.now()
                .plusSeconds(30)
        );

    mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    json(
                        Map.of(
                            "email",
                            EMAIL,
                            "password",
                            PASSWORD,
                            "mfaCode",
                            nextCode
                        )
                    )
                )
        )
        .andExpect(
            status().isOk()
        )
        .andExpect(
            jsonPath("$.accessToken")
                .isNotEmpty()
        );
}


}