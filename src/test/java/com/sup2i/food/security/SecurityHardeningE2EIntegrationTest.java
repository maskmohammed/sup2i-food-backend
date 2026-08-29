package com.sup2i.food.security;

import com.sup2i.food.common.api.RequestTrace;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.security.api.dto.AuthResponse;
import com.sup2i.food.security.api.dto.ForgotPasswordResponse;
import com.sup2i.food.security.ratelimit.RateLimitService;
import com.sup2i.food.security.service.RefreshTokenService;
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
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13 - Hardening sécurité & exploitation.
 *
 * Vérifie les protections ajoutées (ratelimit Bucket4j, policy mot de passe,
 * audience JWT, audit trail, headers de sécurité, XSS/SQLi) et les garanties
 * existantes (escalade horizontale).
 */
@Testcontainers
@SpringBootTest(
    properties = {
        "sup2i.security.local-provider-code=local",
        "sup2i.security.jwt.issuer=sup2i-food-backend-hardening",
        "sup2i.security.jwt.access-token-ttl=15m",
        "sup2i.security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "sup2i.security.refresh-token-ttl=30d",
        "sup2i.security.mfa.enabled=false",
        "sup2i.security.mfa.required-roles=ADMINISTRATION,SYSTEM_ADMIN",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.recovery-code-count=10",
        "sup2i.security.login-protection.enabled=true",
        "sup2i.security.login-protection.max-failed-attempts=30",
        "sup2i.security.login-protection.failure-window=15m",
        "sup2i.security.rate-limit.enabled=true",
        "sup2i.security.cors.enabled=true",
        "sup2i.security.cors.allowed-origins=http://localhost:3000"
    }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SecurityHardeningE2EIntegrationTest {

    private static final String LOCAL_EMAIL_PREFIX =
        "harden.local";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName(
                "sup2i_food_test"
            )
            .withUsername("sup2i_food_test")
            .withPassword("sup2i_food_test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RateLimitService rateLimitService;

    private UUID orgA;
    private UUID orgB;
    private UUID campusA;
    private Actor adminA;
    private Actor victimA;
    private Actor victimB;
    private Actor reviewer;
    private UUID productA;
    private String localEmail;
    private String localPassword;

    @BeforeEach
    void seedTenant() {

        rateLimitService.reset();

        orgA =
            insertOrganization("HARDA");
        orgB =
            insertOrganization("HARDB");
        campusA =
            insertCampus(orgA, "HARDA-C");

        adminA =
            insertActor(
                orgA,
                "ADMIN",
                true
            );
        victimA =
            insertActor(
                orgA,
                "VICTA",
                false
            );
        victimB =
            insertActor(
                orgB,
                "VICTB",
                false
            );

        UUID categoryId =
            insertCategory(
                orgA,
                "HARDA-CAT"
            );

        productA =
            insertProduct(
                orgA,
                categoryId,
                "HARD-SKU"
            );

        reviewer =
            insertActor(
                orgA,
                "REVIEW",
                false
            );

        insertStudent(
            reviewer.userId(),
            campusA
        );

        insertLocalLogin();
    }

    // =========================================================
    // JWT AUDIENCE
    // =========================================================

    @Test
    void forgedJwtWithoutAudienceIsRejected()
        throws Exception {

        AuthResponse auth =
            login(localPassword);

        Jwt real =
            jwtDecoder.decode(
                auth.accessToken()
            );

        Map<String, Object> claims =
            new HashMap<>(
                real.getClaims()
            );

        claims.remove("aud");

        String forged =
            encode(buildClaims(claims));

        mockMvc.perform(
                get("/api/v1/me")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(forged)
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

    @Test
    void forgedJwtWithWrongAudienceIsRejected()
        throws Exception {

        AuthResponse auth =
            login(localPassword);

        Jwt real =
            jwtDecoder.decode(
                auth.accessToken()
            );

        Map<String, Object> claims =
            new HashMap<>(
                real.getClaims()
            );

        claims.put(
            "aud",
            List.of(
                "some-other-service"
            )
        );

        String forged =
            encode(buildClaims(claims));

        mockMvc.perform(
                get("/api/v1/me")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(forged)
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

    @Test
    void reencodedJwtWithValidAudienceIsAccepted()
        throws Exception {

        AuthResponse auth =
            login(localPassword);

        Jwt real =
            jwtDecoder.decode(
                auth.accessToken()
            );

        String reencoded =
            encode(
                buildClaims(
                    new HashMap<>(
                        real.getClaims()
                    )
                )
            );

        mockMvc.perform(
                get("/api/v1/me")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(reencoded)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.email")
                    .value(localEmail)
            );
    }

    // =========================================================
    // SECURITY HEADERS + CACHE CONTROL
    // =========================================================

    @Test
    void securityHeadersArePresent()
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
                                localEmail,
                                "password",
                                localPassword
                            )
                        )
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                header().string(
                    "X-Frame-Options",
                    "DENY"
                )
            )
            .andExpect(
                header().string(
                    "X-Content-Type-Options",
                    "nosniff"
                )
            )
            .andExpect(
                header().string(
                    "Cache-Control",
                    "no-store"
                )
            )
            .andExpect(
                header().string(
                    "Referrer-Policy",
                    "no-referrer"
                )
            );

        mockMvc.perform(
                get("/api/v1/me")
            )
            .andExpect(
                status().isUnauthorized()
            )
            .andExpect(
                header().string(
                    "X-Frame-Options",
                    "DENY"
                )
            )
            .andExpect(
                header().string(
                    "Content-Security-Policy",
                    org.hamcrest.Matchers
                        .containsString(
                            "default-src 'none'"
                        )
                )
            );
    }

    @Test
    void refreshResponseIsNoStore()
        throws Exception {

        AuthResponse auth =
            login(localPassword);

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
                status().isOk()
            )
            .andExpect(
                header().string(
                    "Cache-Control",
                    "no-store"
                )
            );
    }

    // =========================================================
    // ESCALADE HORIZONTALE + AUDIT TRAIL
    // =========================================================

    @Test
    void horizontalEscalationAcrossOrganizationsIsRejected()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/admin/users/"
                        + victimB.userId()
                        + "/deactivate"
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(adminA.accessToken())
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/users/"
                        + victimB.userId()
                        + "/roles"
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(adminA.accessToken())
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "roleCode",
                                "ADMINISTRATION"
                            )
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            );

        String statusB =
            jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM users
                WHERE id = ?
                """,
                String.class,
                victimB.userId()
            );

        assertThat(statusB)
            .isEqualTo("ACTIVE");
    }

    @Test
    void auditTrailCapturesAdminMutations()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/admin/users/"
                        + victimA.userId()
                        + "/deactivate"
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(adminA.accessToken())
                    )
            )
            .andExpect(
                status().isOk()
            );

        Long deactivated =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_logs
                WHERE resource_id = ?
                  AND action = 'USER_DEACTIVATED'
                  AND before_data ->> 'status' = 'ACTIVE'
                  AND after_data ->> 'status' = 'SUSPENDED'
                """,
                Long.class,
                victimA.userId()
            );

        assertThat(deactivated)
            .isEqualTo(1L);

        mockMvc.perform(
                post(
                    "/api/v1/admin/users/"
                        + victimA.userId()
                        + "/activate"
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(adminA.accessToken())
                    )
            )
            .andExpect(
                status().isOk()
            );

        Long activated =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_logs
                WHERE resource_id = ?
                  AND action = 'USER_ACTIVATED'
                  AND before_data ->> 'status' = 'SUSPENDED'
                  AND after_data ->> 'status' = 'ACTIVE'
                  AND ip_address IS NOT NULL
                """,
                Long.class,
                victimA.userId()
            );

        assertThat(activated)
            .isEqualTo(1L);
    }

    // =========================================================
    // PASSWORD POLICY
    // =========================================================

    @Test
    void passwordPolicyRejectsWeakPasswordOnReset()
        throws Exception {

        String token =
            forgotToken(localEmail);

        assertThat(token)
            .isNotBlank();

        mockMvc.perform(
                post(
                    "/api/v1/auth/reset-password"
                )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "token",
                                token,
                                "newPassword",
                                "12345678"
                            )
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

        mockMvc.perform(
                post(
                    "/api/v1/auth/reset-password"
                )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "token",
                                token,
                                "newPassword",
                                "HardenedReset!2026"
                            )
                        )
                    )
            )
            .andExpect(
                status().isNoContent()
            );
    }

    // =========================================================
    // RATE LIMITING (Bucket4j)
    // =========================================================

    @Test
    void rateLimitReturns429OnForgotPassword()
        throws Exception {

        for (int attempt = 0;
             attempt < 10;
             attempt++) {

            mockMvc.perform(
                    post(
                        "/api/v1/auth/forgot-password"
                    )
                        .header(
                            "X-Forwarded-For",
                            "203.0.113.77"
                        )
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            json(
                                Map.of(
                                    "email",
                                    "overflow-"
                                        + attempt
                                        + "@sup2i.test"
                                )
                            )
                        )
                )
                .andExpect(
                    status().isOk()
                );
        }

        MvcResult blocked =
            mockMvc.perform(
                    post(
                        "/api/v1/auth/forgot-password"
                    )
                        .header(
                            "X-Forwarded-For",
                            "203.0.113.77"
                        )
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            json(
                                Map.of(
                                    "email",
                                    "overflow-11@sup2i.test"
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
                )
                .andReturn();

        String retryAfter =
            blocked.getResponse()
                .getHeader(
                    "Retry-After"
                );

        assertThat(retryAfter)
            .isNotNull();

        assertThat(
            Integer.parseInt(retryAfter)
        ).isPositive();

        String traceId =
            blocked.getResponse()
                .getHeader(
                    RequestTrace.HEADER
                );

        assertThat(traceId)
            .isNotBlank();
    }

    // =========================================================
    // XSS
    // =========================================================

    @Test
    void xssPayloadOnReviewCommentIsRejected()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/reviews")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(reviewer.accessToken())
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "productId",
                                productA,
                                "rating",
                                5,
                                "comment",
                                "<script>alert('xss')</script>"
                            )
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

        mockMvc.perform(
                post("/api/v1/reviews")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(reviewer.accessToken())
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "productId",
                                productA,
                                "rating",
                                5,
                                "comment",
                                "Excellent produit, très frais."
                            )
                        )
                    )
            )
            .andExpect(
                status().isOk()
            );
    }

    // =========================================================
    // SQLi
    // =========================================================

    @Test
    void sqlInjectionCannotBypassAuthentication()
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
                                "admin' OR '1'='1' --",
                                "password",
                                "whatever"
                            )
                        )
                    )
            )
            .andExpect(
                status().is4xxClientError()
            );

        mockMvc.perform(
                post(
                    "/api/v1/auth/forgot-password"
                )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        json(
                            Map.of(
                                "email",
                                "x'; DROP TABLE users; --"
                            )
                        )
                    )
            )
            .andExpect(
                status().isBadRequest()
            );

        Long users =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users",
                Long.class
            );

        assertThat(users)
            .isNotZero();
    }

    // =========================================================
    // ACTUATOR
    // =========================================================

    @Test
    void healthIsPublicAndMetricsAreProtected()
        throws Exception {

        mockMvc.perform(
                get("/actuator/health")
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("UP")
            );

        mockMvc.perform(
                get("/actuator/metrics")
            )
            .andExpect(
                status().isUnauthorized()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("UNAUTHORIZED")
            );

        mockMvc.perform(
                get("/actuator/info")
            )
            .andExpect(
                status().isUnauthorized()
            );
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String encode(
        JwtClaimsSet claims
    ) {
        return jwtEncoder.encode(
            JwtEncoderParameters.from(
                claims
            )
        ).getTokenValue();
    }

    private JwtClaimsSet buildClaims(
        Map<String, Object> claims
    ) {
        return JwtClaimsSet.builder()
            .claims(raw ->
                raw.putAll(claims)
            )
            .build();
    }

    private AuthResponse login(String password)
        throws Exception {

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
                                    localEmail,
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

    private String forgotToken(String email)
        throws Exception {

        MvcResult result =
            mockMvc.perform(
                    post(
                        "/api/v1/auth/forgot-password"
                    )
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            json(
                                Map.of(
                                    "email",
                                    email
                                )
                            )
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn();

        return jsonMapper
            .readValue(
                result.getResponse()
                    .getContentAsString(),
                ForgotPasswordResponse.class
            )
            .devResetToken();
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

    private UUID insertCampus(
        UUID orgId,
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
                timezone,
                is_active
            )
            VALUES (?, ?, ?, ?, 'Africa/Casablanca', TRUE)
            """,
            id,
            orgId,
            prefix + " Campus",
            prefix + randomSuffix()
        );

        return id;
    }

    private Actor insertActor(
        UUID orgId,
        String prefix,
        boolean withAdministrationRole
    ) {

        UUID userId =
            UUID.randomUUID();

        String email =
            "harden-"
                + prefix.toLowerCase()
                + "-"
                + randomSuffix()
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
            orgId,
            email,
            "Hardening",
            prefix
        );

        if (withAdministrationRole) {

            UUID roleId =
                jdbcTemplate.queryForObject(
                    """
                    SELECT id
                    FROM roles
                    WHERE code = 'ADMINISTRATION'
                    """,
                    UUID.class
                );

            jdbcTemplate.update(
                """
                INSERT INTO user_roles (
                    id,
                    user_id,
                    role_id
                )
                VALUES (?, ?, ?)
                """,
                UUID.randomUUID(),
                userId,
                roleId
            );
        }

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        String token =
            refreshTokenService
                .issue(
                    user,
                    "harden-e2e-"
                        + prefix,
                    InetAddress
                        .getLoopbackAddress()
                )
                .accessToken();

        return new Actor(
            userId,
            token
        );
    }

    private void insertStudent(
        UUID userId,
        UUID campusId
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO students (
                id,
                user_id,
                campus_id,
                student_number,
                program,
                level,
                group_name,
                enrollment_status
            )
            VALUES (?, ?, ?, ?, 'HARD', 'TEST', 'HARD', 'ACTIVE')
            """,
            UUID.randomUUID(),
            userId,
            campusId,
            randomSuffix()
        );
    }

    private UUID insertCategory(
        UUID orgId,
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
                display_order,
                is_active
            )
            VALUES (?, ?, ?, ?, 0, TRUE)
            """,
            id,
            orgId,
            prefix + " Cat",
            prefix + randomSuffix()
        );

        return id;
    }

    private UUID insertProduct(
        UUID orgId,
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
            orgId,
            categoryId,
            prefix + randomSuffix(),
            prefix + " Product"
        );

        return id;
    }

    private void insertLocalLogin() {

        UUID userId =
            UUID.randomUUID();

        localPassword =
            "Sup2iHarden!2026";

        localEmail =
            LOCAL_EMAIL_PREFIX
                + "."
                + randomSuffix()
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
            VALUES (?, ?, ?, 'Local', 'Login', 'ACTIVE')
            """,
            userId,
            orgA,
            localEmail
        );

        jdbcTemplate.update(
            """
            INSERT INTO auth_identities (
                id,
                user_id,
                provider_type,
                provider_code,
                login_identifier,
                password_hash,
                is_verified,
                is_primary,
                is_active
            )
            VALUES (?, ?, 'LOCAL', 'local', ?, ?, TRUE, TRUE, TRUE)
            """,
            UUID.randomUUID(),
            userId,
            localEmail,
            passwordEncoder.encode(
                localPassword
            )
        );
    }

    private String json(Object value)
        throws Exception {

        return jsonMapper
            .writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String randomSuffix() {

        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10);
    }

    private record Actor(
        UUID userId,
        String accessToken
    ) {
    }
}