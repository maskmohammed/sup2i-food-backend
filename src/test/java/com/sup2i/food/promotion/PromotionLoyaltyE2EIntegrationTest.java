package com.sup2i.food.promotion;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class PromotionLoyaltyE2EIntegrationTest {

    private static final ZoneId CAMPUS_ZONE =
        ZoneId.of(
            "Africa/Casablanca"
        );

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
    private UUID secondLocationId;

    private Actor student;
    private Actor noPermissionStudent;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "B10"
            );

        campusId =
            insertCampus(
                organizationId,
                "MAIN"
            );

        locationId =
            insertLocation(
                campusId,
                "MAIN"
            );

        secondLocationId =
            insertLocation(
                campusId,
                "SECOND"
            );

        student =
            insertActor(
                organizationId,
                campusId,
                "STUDENT",
                "promotion.read",
                "loyalty.read"
            );

        noPermissionStudent =
            insertActor(
                organizationId,
                campusId,
                "NOAUTH"
            );
    }

    // =========================================================
    // 01 - OPENAPI PROMOTION SHAPE
    // =========================================================

    @Test
    void activePromotionReturnsExactOpenApiShape()
        throws Exception {

        OffsetDateTime now =
            now();

        UUID promotionId =
            insertPromotion(
                "ACTIVE SHAPE",
                "PERCENTAGE",
                "ACTIVE",
                now.minusHours(1),
                now.plusHours(1),
                10,
                true,
                true
            );

        mockMvc.perform(
                get(
                    "/api/v1/promotions/active"
                )
                    .param(
                        "locationId",
                        locationId.toString()
                    )
                    .header(
                        "Authorization",
                        bearer(student)
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
                jsonPath("$[0].id")
                    .value(
                        promotionId.toString()
                    )
            )
            .andExpect(
                jsonPath("$[0].name")
                    .value(
                        "ACTIVE SHAPE"
                    )
            )
            .andExpect(
                jsonPath("$[0].type")
                    .value(
                        "PERCENTAGE"
                    )
            )
            .andExpect(
                jsonPath("$[0].startsAt")
                    .exists()
            )
            .andExpect(
                jsonPath("$[0].endsAt")
                    .exists()
            )
            .andExpect(
                jsonPath("$[0].stackable")
                    .value(true)
            );
    }

    // =========================================================
    // 02 - LIFECYCLE / TIME / MOBILE CHANNEL
    // =========================================================

    @Test
    void promotionLifecycleDatesAndMobileChannelAreEnforced()
        throws Exception {

        OffsetDateTime now =
            now();

        insertPromotion(
            "VISIBLE",
            "FIXED_AMOUNT",
            "ACTIVE",
            now.minusHours(1),
            now.plusHours(1),
            10,
            false,
            true
        );

        insertPromotion(
            "DRAFT",
            "FIXED_AMOUNT",
            "DRAFT",
            now.minusHours(1),
            now.plusHours(1),
            20,
            false,
            true
        );

        insertPromotion(
            "FUTURE",
            "FIXED_AMOUNT",
            "ACTIVE",
            now.plusHours(1),
            now.plusHours(2),
            30,
            false,
            true
        );

        insertPromotion(
            "EXPIRED BY TIME",
            "FIXED_AMOUNT",
            "ACTIVE",
            now.minusHours(2),
            now.minusHours(1),
            40,
            false,
            true
        );

        insertPromotion(
            "POS ONLY",
            "FIXED_AMOUNT",
            "ACTIVE",
            now.minusHours(1),
            now.plusHours(1),
            50,
            false,
            false
        );

        mockMvc.perform(
                get(
                    "/api/v1/promotions/active"
                )
                    .param(
                        "locationId",
                        locationId.toString()
                    )
                    .header(
                        "Authorization",
                        bearer(student)
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
                jsonPath("$[0].name")
                    .value(
                        "VISIBLE"
                    )
            );
    }

    // =========================================================
    // 03 - LOCATION TARGET + TENANT ISOLATION
    // =========================================================

    @Test
    void locationTargetingAndTenantIsolationAreEnforced()
        throws Exception {

        OffsetDateTime now =
            now();

        UUID visible =
            insertPromotion(
                "LOCATION MATCH",
                "PERCENTAGE",
                "ACTIVE",
                now.minusHours(1),
                now.plusHours(1),
                20,
                false,
                true
            );

        insertTarget(
            visible,
            "LOCATION",
            locationId,
            true
        );

        UUID hidden =
            insertPromotion(
                "LOCATION MISS",
                "PERCENTAGE",
                "ACTIVE",
                now.minusHours(1),
                now.plusHours(1),
                10,
                false,
                true
            );

        insertTarget(
            hidden,
            "LOCATION",
            secondLocationId,
            true
        );

        mockMvc.perform(
                get(
                    "/api/v1/promotions/active"
                )
                    .param(
                        "locationId",
                        locationId.toString()
                    )
                    .header(
                        "Authorization",
                        bearer(student)
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
                jsonPath("$[0].id")
                    .value(
                        visible.toString()
                    )
            );

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN"
            );

        UUID foreignCampus =
            insertCampus(
                foreignOrganization,
                "FOREIGN"
            );

        UUID foreignLocation =
            insertLocation(
                foreignCampus,
                "FOREIGN"
            );

        mockMvc.perform(
                get(
                    "/api/v1/promotions/active"
                )
                    .param(
                        "locationId",
                        foreignLocation.toString()
                    )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(0)
            );
    }

    // =========================================================
    // 04 - STUDENT + SEGMENT TARGETS
    // =========================================================

    @Test
    void studentAndSegmentTargetsAreApplied()
        throws Exception {

        OffsetDateTime now =
            now();

        UUID directStudent =
            insertPromotion(
                "DIRECT STUDENT",
                "PERCENTAGE",
                "ACTIVE",
                now.minusHours(1),
                now.plusHours(1),
                30,
                false,
                true
            );

        insertTarget(
            directStudent,
            "STUDENT",
            student.studentId(),
            true
        );

        UUID segmentId =
            insertSegment(
                "B10 TARGET"
            );

        insertSegmentMembership(
            segmentId,
            student.studentId(),
            now.minusHours(2),
            now.plusHours(2)
        );

        UUID segmentPromotion =
            insertPromotion(
                "SEGMENT STUDENT",
                "PERCENTAGE",
                "ACTIVE",
                now.minusHours(1),
                now.plusHours(1),
                20,
                false,
                true
            );

        insertTarget(
            segmentPromotion,
            "STUDENT_SEGMENT",
            segmentId,
            true
        );

        UUID otherStudentPromotion =
            insertPromotion(
                "OTHER STUDENT",
                "PERCENTAGE",
                "ACTIVE",
                now.minusHours(1),
                now.plusHours(1),
                10,
                false,
                true
            );

        insertTarget(
            otherStudentPromotion,
            "STUDENT",
            noPermissionStudent.studentId(),
            true
        );

        mockMvc.perform(
                get(
                    "/api/v1/promotions/active"
                )
                    .param(
                        "locationId",
                        locationId.toString()
                    )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(2)
            )
            .andExpect(
                jsonPath("$[*].name")
                    .value(
                        containsInAnyOrder(
                            "DIRECT STUDENT",
                            "SEGMENT STUDENT"
                        )
                    )
            );
    }

    // =========================================================
    // 05 - INCLUDE / EXCLUDE TARGET SEMANTICS
    // =========================================================

    @Test
    void explicitExclusionOverridesMatchingInclusion()
        throws Exception {

        OffsetDateTime now =
            now();

        UUID promotionId =
            insertPromotion(
                "EXCLUDED STUDENT",
                "PERCENTAGE",
                "ACTIVE",
                now.minusHours(1),
                now.plusHours(1),
                10,
                false,
                true
            );

        insertTarget(
            promotionId,
            "LOCATION",
            locationId,
            true
        );

        insertTarget(
            promotionId,
            "STUDENT",
            student.studentId(),
            false
        );

        mockMvc.perform(
                get(
                    "/api/v1/promotions/active"
                )
                    .param(
                        "locationId",
                        locationId.toString()
                    )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(0)
            );
    }

    // =========================================================
    // 06 - CAMPUS-LOCAL SCHEDULE
    // =========================================================

    @Test
    void scheduleWindowUsesCampusLocalDay()
        throws Exception {

        OffsetDateTime now =
            now();

        int currentDay =
            ZonedDateTime
                .now(
                    CAMPUS_ZONE
                )
                .getDayOfWeek()
                .getValue();

        int differentDay =
            currentDay == 7
                ? 1
                : currentDay + 1;

        UUID allowed =
            insertPromotion(
                "SCHEDULE ALLOWED",
                "FIXED_AMOUNT",
                "ACTIVE",
                now.minusHours(1),
                now.plusHours(1),
                20,
                false,
                true
            );

        insertSchedule(
            allowed,
            currentDay
        );

        UUID blocked =
            insertPromotion(
                "SCHEDULE BLOCKED",
                "FIXED_AMOUNT",
                "ACTIVE",
                now.minusHours(1),
                now.plusHours(1),
                10,
                false,
                true
            );

        insertSchedule(
            blocked,
            differentDay
        );

        mockMvc.perform(
                get(
                    "/api/v1/promotions/active"
                )
                    .param(
                        "locationId",
                        locationId.toString()
                    )
                    .header(
                        "Authorization",
                        bearer(student)
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
                jsonPath("$[0].id")
                    .value(
                        allowed.toString()
                    )
            );
    }

    // =========================================================
    // 07 - RBAC BOTH READ SURFACES
    // =========================================================

    @Test
    void promotionAndLoyaltyReadsRequireTheirPermissions()
        throws Exception {

        mockMvc.perform(
                get(
                    "/api/v1/promotions/active"
                )
                    .param(
                        "locationId",
                        locationId.toString()
                    )
                    .header(
                        "Authorization",
                        bearer(noPermissionStudent)
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                get(
                    "/api/v1/me/loyalty"
                )
                    .header(
                        "Authorization",
                        bearer(noPermissionStudent)
                    )
            )
            .andExpect(
                status().isForbidden()
            );
    }

    // =========================================================
    // 08 - LOYALTY ZERO + LEDGER HISTORY
    // =========================================================

    @Test
    void loyaltyReturnsZeroWithoutAccountAndThenReadsLedgerNewestFirst()
        throws Exception {

        mockMvc.perform(
                get(
                    "/api/v1/me/loyalty"
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.balance")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.transactions.length()")
                    .value(0)
            );

        UUID accountId =
            insertLoyaltyAccount(
                student.studentId(),
                60
            );

        OffsetDateTime older =
            now()
                .minusMinutes(10);

        OffsetDateTime newer =
            now()
                .minusMinutes(1);

        insertLoyaltyTransaction(
            accountId,
            "EARN",
            100,
            "Snack purchase",
            older
        );

        insertLoyaltyTransaction(
            accountId,
            "REDEEM",
            -40,
            "Reward redemption",
            newer
        );

        mockMvc.perform(
                get(
                    "/api/v1/me/loyalty"
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.balance")
                    .value(60)
            )
            .andExpect(
                jsonPath("$.transactions.length()")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.transactions[0].type")
                    .value(
                        "REDEEM"
                    )
            )
            .andExpect(
                jsonPath("$.transactions[0].points")
                    .value(-40)
            )
            .andExpect(
                jsonPath("$.transactions[0].reason")
                    .value(
                        "Reward redemption"
                    )
            )
            .andExpect(
                jsonPath("$.transactions[0].createdAt")
                    .exists()
            )
            .andExpect(
                jsonPath("$.transactions[1].type")
                    .value(
                        "EARN"
                    )
            )
            .andExpect(
                jsonPath("$.transactions[1].points")
                    .value(100)
            )
            .andExpect(
                jsonPath("$.transactions[1].reason")
                    .value(
                        "Snack purchase"
                    )
            );
    }

    // =========================================================
    // PROMOTION FIXTURES
    // =========================================================

    private UUID insertPromotion(
        String name,
        String type,
        String status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        int priority,
        boolean stackable,
        boolean mobileEnabled
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO promotions (
                id,
                organization_id,
                name,
                code,
                type,
                status,
                starts_at,
                ends_at,
                priority,
                stackable,
                created_by,
                mobile_enabled,
                pos_enabled
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE
            )
            """,
            id,
            organizationId,
            name,
            "B10-PROMO-"
                + randomSuffix(),
            type,
            status,
            startsAt,
            endsAt,
            priority,
            stackable,
            student.userId(),
            mobileEnabled
        );

        return id;
    }

    private void insertTarget(
        UUID promotionId,
        String targetType,
        UUID targetId,
        boolean include
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO promotion_targets (
                id,
                promotion_id,
                target_type,
                target_id,
                include_target
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            promotionId,
            targetType,
            targetId,
            include
        );
    }

    private void insertSchedule(
        UUID promotionId,
        int dayOfWeek
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO promotion_schedule_windows (
                id,
                promotion_id,
                day_of_week,
                starts_at_time,
                ends_at_time
            )
            VALUES (
                ?, ?, ?,
                NULL,
                NULL
            )
            """,
            UUID.randomUUID(),
            promotionId,
            dayOfWeek
        );
    }

    private UUID insertSegment(
        String name
    ) {

        UUID segmentId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO student_segments (
                id,
                organization_id,
                code,
                name,
                segment_type,
                rule_config,
                is_active
            )
            VALUES (
                ?, ?, ?, ?,
                'MANUAL',
                NULL,
                TRUE
            )
            """,
            segmentId,
            organizationId,
            "B10-SEG-"
                + randomSuffix(),
            name
        );

        return segmentId;
    }

    private void insertSegmentMembership(
        UUID segmentId,
        UUID studentId,
        OffsetDateTime validFrom,
        OffsetDateTime validTo
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO student_segment_memberships (
                segment_id,
                student_id,
                source,
                valid_from,
                valid_to
            )
            VALUES (
                ?, ?,
                'MANUAL',
                ?, ?
            )
            """,
            segmentId,
            studentId,
            validFrom,
            validTo
        );
    }

    // =========================================================
    // LOYALTY FIXTURES
    // =========================================================

    private UUID insertLoyaltyAccount(
        UUID studentId,
        int currentBalance
    ) {

        UUID accountId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO loyalty_accounts (
                id,
                student_id,
                status,
                current_balance,
                lifetime_earned,
                lifetime_redeemed
            )
            VALUES (
                ?, ?,
                'ACTIVE',
                ?,
                100,
                40
            )
            """,
            accountId,
            studentId,
            currentBalance
        );

        return accountId;
    }

    private void insertLoyaltyTransaction(
        UUID accountId,
        String type,
        int points,
        String reason,
        OffsetDateTime createdAt
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO loyalty_transactions (
                id,
                account_id,
                type,
                points,
                reason,
                created_by,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            accountId,
            type,
            points,
            reason,
            student.userId(),
            createdAt
        );
    }

    // =========================================================
    // TENANT / IDENTITY / JWT / RBAC
    // =========================================================

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
            prefix
                + " Organization "
                + suffix,
            prefix
                + "-"
                + suffix
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
                timezone,
                is_active
            )
            VALUES (
                ?, ?, ?, ?,
                'Africa/Casablanca',
                TRUE
            )
            """,
            id,
            tenantId,
            prefix
                + " Campus",
            "B10-C-"
                + randomSuffix()
        );

        return id;
    }

    private UUID insertLocation(
        UUID selectedCampusId,
        String prefix
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
            VALUES (
                ?, ?, ?, ?,
                'SNACK',
                TRUE
            )
            """,
            id,
            selectedCampusId,
            prefix
                + " Location",
            "B10-L-"
                + randomSuffix()
        );

        return id;
    }

    private Actor insertActor(
        UUID tenantId,
        UUID selectedCampusId,
        String prefix,
        String... permissionCodes
    ) {

        UUID userId =
            UUID.randomUUID();

        UUID studentId =
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
            VALUES (
                ?, ?, ?, ?, ?,
                'ACTIVE'
            )
            """,
            userId,
            tenantId,
            "b10-"
                + prefix.toLowerCase()
                + "-"
                + suffix
                + "@sup2i.test",
            "B10",
            prefix
        );

        jdbcTemplate.update(
            """
            INSERT INTO students (
                id,
                user_id,
                campus_id,
                student_number,
                enrollment_status
            )
            VALUES (
                ?, ?, ?, ?,
                'ACTIVE'
            )
            """,
            studentId,
            userId,
            selectedCampusId,
            "B10-"
                + suffix
        );

        UUID roleId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO roles (
                id,
                code,
                name,
                is_system
            )
            VALUES (?, ?, ?, FALSE)
            """,
            roleId,
            "B10_ROLE_"
                + randomSuffix(),
            "B10 E2E Role"
        );

        for (
            String permissionCode
                : permissionCodes
        ) {

            UUID candidatePermissionId =
                UUID.randomUUID();

            jdbcTemplate.update(
                """
                INSERT INTO permissions (
                    id,
                    code,
                    description
                )
                VALUES (?, ?, ?)
                ON CONFLICT (code)
                DO NOTHING
                """,
                candidatePermissionId,
                permissionCode,
                "B10 E2E permission"
            );

            UUID permissionId =
                jdbcTemplate.queryForObject(
                    """
                    SELECT id
                    FROM permissions
                    WHERE code = ?
                    """,
                    UUID.class,
                    permissionCode
                );

            jdbcTemplate.update(
                """
                INSERT INTO role_permissions (
                    role_id,
                    permission_id
                )
                VALUES (?, ?)
                """,
                roleId,
                permissionId
            );
        }

        jdbcTemplate.update(
            """
            INSERT INTO user_roles (
                id,
                user_id,
                role_id,
                campus_id,
                location_id
            )
            VALUES (
                ?, ?, ?,
                NULL,
                NULL
            )
            """,
            UUID.randomUUID(),
            userId,
            roleId
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
                "b10-e2e",
                InetAddress
                    .getLoopbackAddress()
            );

        return new Actor(
            userId,
            studentId,
            tokens.accessToken()
        );
    }

    private String bearer(
        Actor actor
    ) {

        return "Bearer "
            + actor.accessToken();
    }

    private OffsetDateTime now() {

        return ZonedDateTime
            .now(
                CAMPUS_ZONE
            )
            .toOffsetDateTime();
    }

    private String randomSuffix() {

        String value =
            UUID.randomUUID()
                .toString()
                .replace(
                    "-",
                    ""
                );

        return value.substring(
            0,
            12
        );
    }

    private record Actor(
        UUID userId,
        UUID studentId,
        String accessToken
    ) {
    }
}
