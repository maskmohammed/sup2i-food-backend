package com.sup2i.food.canteen;

import com.sup2i.food.canteen.api.dto.MealDistributionRequest;
import com.sup2i.food.canteen.api.dto.MealUsageResponse;
import com.sup2i.food.canteen.exception.CanteenErrorCode;
import com.sup2i.food.canteen.exception.CanteenException;
import com.sup2i.food.canteen.service.MealDistributionService;
import com.sup2i.food.canteen.service.MealEligibilityService;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.scan.service.ScanTokenHasher;
import com.sup2i.food.security.service.AuthenticationTokens;
import com.sup2i.food.security.service.RefreshTokenService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.InetAddress;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class CanteenE2EIntegrationTest {

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

    @Autowired
    private ScanTokenHasher tokenHasher;

    @Autowired
    private MealDistributionService distributionService;

    @Autowired
    private MealEligibilityService eligibilityService;

    private UUID organizationId;
    private UUID campusId;
    private UUID locationId;
    private UUID terminalId;

    private Actor student;
    private Actor manager;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "B8"
            );

        campusId =
            insertCampus(
                organizationId,
                "MAIN"
            );

        locationId =
            insertLocation(
                campusId,
                "CANTEEN"
            );

        terminalId =
            insertTerminal(
                locationId
            );

        student =
            insertActor(
                organizationId,
                campusId,
                true,
                "STUDENT",
                "canteen.menu.read",
                "canteen.reserve"
            );

        manager =
            insertActor(
                organizationId,
                campusId,
                false,
                "MANAGER",
                "canteen.distribute"
            );
    }

    // =========================================================
    // 01 - MENU READ / FINAL CHOICES / TENANT
    // =========================================================

    @Test
    void menuReadProjectsFinalChoicesAndIsTenantSafe()
        throws Exception {

        LocalDate date =
            campusToday()
                .plusDays(1);

        UUID productId =
            insertProduct(
                organizationId,
                "MENU"
            );

        UUID menuId =
            insertMenu(
                locationId,
                date,
                "LUNCH",
                "PUBLISHED"
            );

        insertMenuChoice(
            menuId,
            productId
        );

        mockMvc.perform(
                get(
                    "/api/v1/canteen/menus"
                )
                    .param(
                        "locationId",
                        locationId.toString()
                    )
                    .param(
                        "from",
                        date.toString()
                    )
                    .param(
                        "to",
                        date.toString()
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
                jsonPath("$[0].id")
                    .value(
                        menuId.toString()
                    )
            )
            .andExpect(
                jsonPath("$[0].mealType")
                    .value("LUNCH")
            )
            .andExpect(
                jsonPath("$[0].status")
                    .value("PUBLISHED")
            )
            .andExpect(
                jsonPath("$[0].products[0].id")
                    .value(
                        productId.toString()
                    )
            )
            .andExpect(
                jsonPath("$[0].products[0].available")
                    .value(true)
            )
            .andExpect(
                jsonPath("$[0].products[0].active")
                    .value(true)
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
                    "/api/v1/canteen/menus"
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
                jsonPath("$")
                    .isEmpty()
            );
    }

    // =========================================================
    // 02 - RESERVATION / IDEMPOTENCY / DUPLICATE
    // =========================================================

    @Test
    void reservationIsIdempotentAndDuplicateSafe()
        throws Exception {

        LocalDate date =
            campusToday()
                .plusDays(1);

        insertSubscription(
            student.studentId(),
            "LUNCH",
            date.minusDays(2),
            date.plusDays(5),
            date.minusDays(2),
            date.plusDays(5),
            5,
            1,
            false
        );

        UUID menuId =
            insertMenu(
                locationId,
                date,
                "LUNCH",
                "PUBLISHED"
            );

        String key =
            key("RES");

        postReservation(
            student,
            key,
            menuId
        )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.menuId")
                    .value(
                        menuId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.studentId")
                    .value(
                        student
                            .studentId()
                            .toString()
                    )
            )
            .andExpect(
                jsonPath("$.status")
                    .value("RESERVED")
            );

        UUID reservationId =
            reservationId(
                student.studentId(),
                menuId
            );

        postReservation(
            student,
            key,
            menuId
        )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.id")
                    .value(
                        reservationId.toString()
                    )
            );

        assertThat(
            reservationCount(
                student.studentId(),
                menuId
            )
        )
            .isEqualTo(1L);

        assertThat(
            idempotencyCount(
                "CANTEEN_RESERVE:"
                    + organizationId,
                key
            )
        )
            .isEqualTo(1L);

        postReservation(
            student,
            key("RES-DUP"),
            menuId
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "CANTEEN_ALREADY_RESERVED"
                    )
            );
    }

    // =========================================================
    // 03 - SUBSCRIPTION + ENTITLEMENT GUARDS
    // =========================================================

    @Test
    void reservationRequiresActiveSubscriptionAndValidEntitlement()
        throws Exception {

        LocalDate today =
            campusToday();

        UUID menuId =
            insertMenu(
                locationId,
                today,
                "LUNCH",
                "PUBLISHED"
            );

        postReservation(
            student,
            key("NO-SUB"),
            menuId
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "SUBSCRIPTION_INACTIVE"
                    )
            );

        insertSubscription(
            student.studentId(),
            "LUNCH",
            today.minusDays(10),
            today.plusDays(10),
            today.minusDays(5),
            today.minusDays(1),
            5,
            1,
            false
        );

        postReservation(
            student,
            key("EXPIRED-ENT"),
            menuId
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "ENTITLEMENT_EXPIRED"
                    )
            );
    }

    // =========================================================
    // 04 - DISTRIBUTION / AUDIT / IDEMPOTENCY
    // =========================================================

    @Test
    void distributionCreatesOneUsageAuditAndExactReplay()
        throws Exception {

        LocalDate today =
            campusToday();

        insertSubscription(
            student.studentId(),
            "LUNCH",
            today.minusDays(2),
            today.plusDays(5),
            today.minusDays(2),
            today.plusDays(5),
            3,
            1,
            false
        );

        UUID menuId =
            insertMenu(
                locationId,
                today,
                "LUNCH",
                "PUBLISHED"
            );

        FoodPassFixture pass =
            insertFoodPass(
                student.studentId(),
                "ACTIVE"
            );

        String key =
            key("DIST");

        postDistribution(
            manager,
            key,
            pass.rawToken(),
            "LUNCH",
            menuId,
            terminalId
        )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.studentId")
                    .value(
                        student
                            .studentId()
                            .toString()
                    )
            )
            .andExpect(
                jsonPath("$.mealType")
                    .value("LUNCH")
            )
            .andExpect(
                jsonPath("$.usageDate")
                    .value(
                        today.toString()
                    )
            )
            .andExpect(
                jsonPath("$.remainingQuota")
                    .value(2)
            );

        UUID usageId =
            validUsageId(
                student.studentId(),
                today,
                "LUNCH"
            );

        postDistribution(
            manager,
            key,
            pass.rawToken(),
            "LUNCH",
            menuId,
            terminalId
        )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.id")
                    .value(
                        usageId.toString()
                    )
            )
            .andExpect(
                jsonPath("$.remainingQuota")
                    .value(2)
            );

        assertThat(
            validUsageCount(
                student.studentId(),
                today,
                "LUNCH"
            )
        )
            .isEqualTo(1L);

        assertThat(
            auditCount(
                usageId
            )
        )
            .isEqualTo(1L);

        assertThat(
            idempotencyCount(
                "CANTEEN_DISTRIBUTE:"
                    + organizationId,
                key
            )
        )
            .isEqualTo(1L);
    }

    // =========================================================
    // 05 - REQUIRED RESERVATION -> CONSUMED
    // =========================================================

    @Test
    void requiredReservationIsConsumedByDistribution()
        throws Exception {

        LocalDate today =
            campusToday();

        insertSubscription(
            student.studentId(),
            "LUNCH",
            today.minusDays(2),
            today.plusDays(5),
            today.minusDays(2),
            today.plusDays(5),
            4,
            1,
            true
        );

        UUID menuId =
            insertMenu(
                locationId,
                today,
                "LUNCH",
                "PUBLISHED"
            );

        postReservation(
            student,
            key("REQ-RES"),
            menuId
        )
            .andExpect(
                status().isCreated()
            );

        UUID reservationId =
            reservationId(
                student.studentId(),
                menuId
            );

        FoodPassFixture pass =
            insertFoodPass(
                student.studentId(),
                "ACTIVE"
            );

        postDistribution(
            manager,
            key("REQ-DIST"),
            pass.rawToken(),
            "LUNCH",
            menuId,
            terminalId
        )
            .andExpect(
                status().isCreated()
            );

        assertThat(
            reservationStatus(
                reservationId
            )
        )
            .isEqualTo(
                "CONSUMED"
            );

        assertThat(
            reservationConsumedAt(
                reservationId
            )
        )
            .isNotNull();

        assertThat(
            usageReservationId(
                student.studentId(),
                today,
                "LUNCH"
            )
        )
            .isEqualTo(
                reservationId
            );
    }

    // =========================================================
    // 06 - REQUIRED RESERVATION MISSING
    // =========================================================

    @Test
    void requiredReservationCannotBeBypassed()
        throws Exception {

        LocalDate today =
            campusToday();

        insertSubscription(
            student.studentId(),
            "LUNCH",
            today.minusDays(2),
            today.plusDays(5),
            today.minusDays(2),
            today.plusDays(5),
            4,
            1,
            true
        );

        UUID menuId =
            insertMenu(
                locationId,
                today,
                "LUNCH",
                "PUBLISHED"
            );

        FoodPassFixture pass =
            insertFoodPass(
                student.studentId(),
                "ACTIVE"
            );

        postDistribution(
            manager,
            key("NO-REQ-RES"),
            pass.rawToken(),
            "LUNCH",
            menuId,
            terminalId
        )
            .andExpect(
                status().isUnprocessableEntity()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "MEAL_NOT_ALLOWED"
                    )
            );

        assertThat(
            validUsageCount(
                student.studentId(),
                today,
                "LUNCH"
            )
        )
            .isZero();
    }

    // =========================================================
    // 07 - FOOD PASS STATES + INVALID TOKEN
    // =========================================================

    @Test
    void invalidAndNonActiveFoodPassesAreRejected()
        throws Exception {

        postDistribution(
            manager,
            key("BAD-QR"),
            "not-a-real-food-pass-"
                + UUID.randomUUID(),
            "LUNCH",
            null,
            terminalId
        )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("INVALID_QR")
            );

        FoodPassFixture blocked =
            insertFoodPass(
                student.studentId(),
                "BLOCKED"
            );

        postDistribution(
            manager,
            key("BLOCKED"),
            blocked.rawToken(),
            "LUNCH",
            null,
            terminalId
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "FOOD_PASS_BLOCKED"
                    )
            );

        FoodPassFixture lost =
            insertFoodPass(
                student.studentId(),
                "LOST"
            );

        postDistribution(
            manager,
            key("LOST"),
            lost.rawToken(),
            "LUNCH",
            null,
            terminalId
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "FOOD_PASS_LOST"
                    )
            );

        FoodPassFixture revoked =
            insertFoodPass(
                student.studentId(),
                "REVOKED"
            );

        postDistribution(
            manager,
            key("REVOKED"),
            revoked.rawToken(),
            "LUNCH",
            null,
            terminalId
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "FOOD_PASS_REVOKED"
                    )
            );

        FoodPassFixture expired =
            insertFoodPass(
                student.studentId(),
                "EXPIRED"
            );

        postDistribution(
            manager,
            key("EXPIRED"),
            expired.rawToken(),
            "LUNCH",
            null,
            terminalId
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "FOOD_PASS_EXPIRED"
                    )
            );
    }

    // =========================================================
    // 08 - QUOTA + DAILY LIMIT
    // =========================================================

    @Test
    void quotaAndDailyLimitAreRecheckedFromValidUsages()
        throws Exception {

        LocalDate today =
            campusToday();

        EntitlementFixture quotaEntitlement =
            insertSubscription(
                student.studentId(),
                "LUNCH",
                today.minusDays(5),
                today.plusDays(5),
                today.minusDays(5),
                today.plusDays(5),
                1,
                1,
                false
            );

        insertUsage(
            quotaEntitlement.entitlementId(),
            student.studentId(),
            today.minusDays(1),
            "LUNCH",
            manager.userId()
        );

        FoodPassFixture pass =
            insertFoodPass(
                student.studentId(),
                "ACTIVE"
            );

        postDistribution(
            manager,
            key("QUOTA"),
            pass.rawToken(),
            "LUNCH",
            null,
            terminalId
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "QUOTA_EXHAUSTED"
                    )
            );

        Actor dailyStudent =
            insertActor(
                organizationId,
                campusId,
                true,
                "DAILY"
            );

        EntitlementFixture dailyEntitlement =
            insertSubscription(
                dailyStudent.studentId(),
                "LUNCH",
                today.minusDays(2),
                today.plusDays(2),
                today.minusDays(2),
                today.plusDays(2),
                10,
                1,
                false
            );

        insertUsage(
            dailyEntitlement.entitlementId(),
            dailyStudent.studentId(),
            today,
            "LUNCH",
            manager.userId()
        );

        assertThatThrownBy(
            () ->
                eligibilityService
                    .requireStudentEligible(
                        organizationId,
                        dailyStudent.studentId(),
                        today,
                        "LUNCH",
                        null
                    )
        )
            .isInstanceOfSatisfying(
                CanteenException.class,
                exception ->
                    assertThat(
                        exception.getErrorCode()
                    )
                        .isEqualTo(
                            CanteenErrorCode
                                .DAILY_LIMIT_REACHED
                        )
            );
    }

    // =========================================================
    // 09 - MENU / TERMINAL / STUDENT STATE
    // =========================================================

    @Test
    void distributionGuardsMenuTerminalAndStudentState()
        throws Exception {

        LocalDate today =
            campusToday();

        insertSubscription(
            student.studentId(),
            "LUNCH",
            today.minusDays(2),
            today.plusDays(5),
            today.minusDays(2),
            today.plusDays(5),
            5,
            1,
            false
        );

        UUID menuId =
            insertMenu(
                locationId,
                today,
                "LUNCH",
                "PUBLISHED"
            );

        FoodPassFixture pass =
            insertFoodPass(
                student.studentId(),
                "ACTIVE"
            );

        postDistribution(
            manager,
            key("BAD-MEAL"),
            pass.rawToken(),
            "BREAKFAST",
            menuId,
            terminalId
        )
            .andExpect(
                status().isUnprocessableEntity()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "MEAL_NOT_ALLOWED"
                    )
            );

        UUID foreignOrganization =
            insertOrganization(
                "TERMINAL-FOREIGN"
            );

        UUID foreignCampus =
            insertCampus(
                foreignOrganization,
                "TERMINAL-FOREIGN"
            );

        UUID foreignLocation =
            insertLocation(
                foreignCampus,
                "TERMINAL-FOREIGN"
            );

        UUID foreignTerminal =
            insertTerminal(
                foreignLocation
            );

        postDistribution(
            manager,
            key("BAD-TERM"),
            pass.rawToken(),
            "LUNCH",
            menuId,
            foreignTerminal
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

        jdbcTemplate.update(
            """
            UPDATE students
            SET enrollment_status = 'SUSPENDED'
            WHERE id = ?
            """,
            student.studentId()
        );

        postDistribution(
            manager,
            key("INACTIVE"),
            pass.rawToken(),
            "LUNCH",
            menuId,
            terminalId
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        "STUDENT_INACTIVE"
                    )
            );
    }

    // =========================================================
    // 10 - CONCURRENCY / TWO KEYS / ONE USAGE
    // =========================================================

    @Test
    void concurrentScansYieldOneSuccessAndOneMealAlreadyUsed()
        throws Exception {

        LocalDate today =
            campusToday();

        insertSubscription(
            student.studentId(),
            "LUNCH",
            today.minusDays(2),
            today.plusDays(5),
            today.minusDays(2),
            today.plusDays(5),
            5,
            1,
            false
        );

        UUID menuId =
            insertMenu(
                locationId,
                today,
                "LUNCH",
                "PUBLISHED"
            );

        FoodPassFixture pass =
            insertFoodPass(
                student.studentId(),
                "ACTIVE"
            );

        CountDownLatch ready =
            new CountDownLatch(2);

        CountDownLatch start =
            new CountDownLatch(1);

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        try {

            Future<DistributionOutcome> first =
                executor.submit(
                    () ->
                        concurrentDistribution(
                            ready,
                            start,
                            key("RACE-A"),
                            pass.rawToken(),
                            menuId
                        )
                );

            Future<DistributionOutcome> second =
                executor.submit(
                    () ->
                        concurrentDistribution(
                            ready,
                            start,
                            key("RACE-B"),
                            pass.rawToken(),
                            menuId
                        )
                );

            boolean bothReady =
                ready.await(
                    10,
                    TimeUnit.SECONDS
                );

            assertThat(bothReady)
                .isTrue();

            start.countDown();

            DistributionOutcome firstResult =
                first.get(
                    30,
                    TimeUnit.SECONDS
                );

            DistributionOutcome secondResult =
                second.get(
                    30,
                    TimeUnit.SECONDS
                );

            List<DistributionOutcome> outcomes =
                List.of(
                    firstResult,
                    secondResult
                );

            long successes =
                outcomes
                    .stream()
                    .filter(
                        DistributionOutcome::success
                    )
                    .count();

            long alreadyUsed =
                outcomes
                    .stream()
                    .filter(
                        outcome ->
                            "MEAL_ALREADY_USED"
                                .equals(
                                    outcome.errorCode()
                                )
                    )
                    .count();

            assertThat(successes)
                .isEqualTo(1);

            assertThat(alreadyUsed)
                .isEqualTo(1);

            assertThat(
                validUsageCount(
                    student.studentId(),
                    today,
                    "LUNCH"
                )
            )
                .isEqualTo(1L);
        }
        finally {

            executor.shutdownNow();
        }
    }

    // =========================================================
    // CONCURRENT CALL
    // =========================================================

    private DistributionOutcome concurrentDistribution(
        CountDownLatch ready,
        CountDownLatch start,
        String idempotencyKey,
        String rawToken,
        UUID menuId
    ) throws Exception {

        ready.countDown();

        boolean released =
            start.await(
                10,
                TimeUnit.SECONDS
            );

        if (!released) {

            throw new IllegalStateException(
                "Concurrency start latch timed out."
            );
        }

        try {

            MealUsageResponse response =
                distributionService
                    .distribute(
                        manager.userId(),
                        idempotencyKey,
                        new MealDistributionRequest(
                            rawToken,
                            "LUNCH",
                            menuId,
                            terminalId
                        )
                    );

            return new DistributionOutcome(
                true,
                null,
                response.id()
            );
        }
        catch (CanteenException exception) {

            return new DistributionOutcome(
                false,
                exception
                    .getErrorCode()
                    .name(),
                null
            );
        }
    }

    // =========================================================
    // HTTP HELPERS
    // =========================================================

    private ResultActions postReservation(
        Actor requestActor,
        String idempotencyKey,
        UUID menuId
    ) throws Exception {

        String body =
            """
            {
              "menuId": "%s"
            }
            """.formatted(
                menuId
            );

        return mockMvc.perform(
            post(
                "/api/v1/canteen/reservations"
            )
                .header(
                    "Authorization",
                    bearer(requestActor)
                )
                .header(
                    "Idempotency-Key",
                    idempotencyKey
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(body)
        );
    }

    private ResultActions postDistribution(
        Actor requestActor,
        String idempotencyKey,
        String rawToken,
        String mealType,
        UUID menuId,
        UUID selectedTerminalId
    ) throws Exception {

        String menuFragment =
            menuId == null
                ? "null"
                : "\""
                    + menuId
                    + "\"";

        String terminalFragment =
            selectedTerminalId == null
                ? "null"
                : "\""
                    + selectedTerminalId
                    + "\"";

        String body =
            """
            {
              "foodPassToken": "%s",
              "mealType": "%s",
              "menuId": %s,
              "terminalId": %s
            }
            """.formatted(
                rawToken,
                mealType,
                menuFragment,
                terminalFragment
            );

        return mockMvc.perform(
            post(
                "/api/v1/canteen/distributions"
            )
                .header(
                    "Authorization",
                    bearer(requestActor)
                )
                .header(
                    "Idempotency-Key",
                    idempotencyKey
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(body)
        );
    }

    private String bearer(
        Actor requestActor
    ) {

        return "Bearer "
            + requestActor.accessToken();
    }

    // =========================================================
    // TENANT / IDENTITY / RBAC
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
            prefix + " Organization " + suffix,
            prefix + "-" + suffix
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
            prefix + " Campus",
            "C-" + randomSuffix()
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
            prefix + " Location",
            "L-" + randomSuffix()
        );

        return id;
    }

    private UUID insertTerminal(
        UUID selectedLocationId
    ) {

        UUID id =
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
            VALUES (
                ?, ?, ?, ?,
                'SUP2I_POS',
                TRUE
            )
            """,
            id,
            selectedLocationId,
            "T-" + randomSuffix(),
            "B8 Terminal"
        );

        return id;
    }

    private Actor insertActor(
        UUID tenantId,
        UUID selectedCampusId,
        boolean createStudent,
        String prefix,
        String... permissionCodes
    ) {

        UUID userId =
            UUID.randomUUID();

        UUID studentId =
            createStudent
                ? UUID.randomUUID()
                : null;

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
            "b8-"
                + prefix.toLowerCase()
                + "-"
                + suffix
                + "@sup2i.test",
            "B8",
            prefix
        );

        if (createStudent) {

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
                "B8-" + suffix
            );
        }

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
            "B8_ROLE_" + randomSuffix(),
            "B8 E2E Role"
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
                "B8 E2E permission"
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
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "b8-e2e",
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
    // CATALOG / MENU
    // =========================================================

    private UUID insertProduct(
        UUID tenantId,
        String prefix
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
            VALUES (
                ?, ?, ?, ?,
                0,
                TRUE
            )
            """,
            categoryId,
            tenantId,
            prefix + " Category",
            "b8-" + randomSuffix()
        );

        UUID productId =
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
            prefix + "-" + randomSuffix(),
            prefix + " Product"
        );

        return productId;
    }

    private UUID insertMenu(
        UUID selectedLocationId,
        LocalDate date,
        String mealType,
        String menuStatus
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO canteen_menus (
                id,
                location_id,
                menu_date,
                meal_type,
                title,
                description,
                status,
                created_by
            )
            VALUES (
                ?, ?, ?, ?, ?, ?,
                ?, ?
            )
            """,
            id,
            selectedLocationId,
            date,
            mealType,
            "B8 Menu " + randomSuffix(),
            "B8 E2E menu",
            menuStatus,
            manager.userId()
        );

        return id;
    }

    private void insertMenuChoice(
        UUID menuId,
        UUID productId
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO canteen_menu_choices (
                id,
                canteen_menu_id,
                product_id,
                label,
                display_order,
                max_reservations,
                is_active
            )
            VALUES (
                ?, ?, ?, ?,
                0,
                NULL,
                TRUE
            )
            """,
            UUID.randomUUID(),
            menuId,
            productId,
            "B8 choice"
        );
    }

    // =========================================================
    // SUBSCRIPTION / ENTITLEMENT
    // =========================================================

    private EntitlementFixture insertSubscription(
        UUID studentId,
        String mealType,
        LocalDate subscriptionStart,
        LocalDate subscriptionEnd,
        LocalDate entitlementStart,
        LocalDate entitlementEnd,
        Integer quota,
        int dailyLimit,
        boolean reservationRequired
    ) {

        UUID planId =
            UUID.randomUUID();

        UUID versionId =
            UUID.randomUUID();

        UUID subscriptionId =
            UUID.randomUUID();

        UUID entitlementId =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

        jdbcTemplate.update(
            """
            INSERT INTO subscription_plans (
                id,
                organization_id,
                name,
                code,
                billing_period,
                price,
                included_meals,
                quota_type,
                quota_value,
                max_per_day,
                reservation_required,
                is_active,
                audience_type,
                quota_period_type,
                renewal_policy,
                suspension_policy
            )
            VALUES (
                ?, ?, ?, ?,
                'MONTH',
                100.00,
                ?,
                'MEALS',
                ?,
                ?,
                ?,
                TRUE,
                'STUDENT',
                'SUBSCRIPTION',
                'MANUAL',
                'BLOCK_USAGE'
            )
            """,
            planId,
            organizationId,
            "B8 Plan " + suffix,
            "B8-" + suffix,
            quota,
            quota,
            dailyLimit,
            reservationRequired
        );

        jdbcTemplate.update(
            """
            INSERT INTO subscription_plan_versions (
                id,
                plan_id,
                version_number,
                audience_type,
                billing_period,
                price,
                included_meals,
                quota_type,
                quota_period_type,
                quota_value,
                max_per_day,
                allowed_days,
                reservation_required,
                renewal_policy,
                suspension_policy
            )
            VALUES (
                ?, ?,
                1,
                'STUDENT',
                'MONTH',
                100.00,
                ?,
                'MEALS',
                'SUBSCRIPTION',
                ?,
                ?,
                NULL,
                ?,
                'MANUAL',
                'BLOCK_USAGE'
            )
            """,
            versionId,
            planId,
            quota,
            quota,
            dailyLimit,
            reservationRequired
        );

        jdbcTemplate.update(
            """
            INSERT INTO subscription_plan_version_services (
                plan_version_id,
                service_type
            )
            VALUES (?, ?)
            """,
            versionId,
            mealType
        );

        jdbcTemplate.update(
            """
            INSERT INTO subscriptions (
                id,
                student_id,
                meal_beneficiary_id,
                plan_id,
                plan_version_id,
                status,
                starts_at,
                ends_at,
                activated_by,
                activated_at
            )
            VALUES (
                ?, ?,
                NULL,
                ?, ?,
                'ACTIVE',
                ?, ?,
                ?,
                CURRENT_TIMESTAMP
            )
            """,
            subscriptionId,
            studentId,
            planId,
            versionId,
            subscriptionStart,
            subscriptionEnd,
            manager.userId()
        );

        jdbcTemplate.update(
            """
            INSERT INTO meal_entitlements (
                id,
                subscription_id,
                meal_type,
                valid_from,
                valid_to,
                allowed_days,
                total_quota,
                daily_limit,
                quota_period_type,
                reservation_required
            )
            VALUES (
                ?, ?, ?, ?, ?,
                NULL,
                ?,
                ?,
                'SUBSCRIPTION',
                ?
            )
            """,
            entitlementId,
            subscriptionId,
            mealType,
            entitlementStart,
            entitlementEnd,
            quota,
            dailyLimit,
            reservationRequired
        );

        return new EntitlementFixture(
            subscriptionId,
            entitlementId,
            planId,
            versionId
        );
    }

    // =========================================================
    // FOOD PASS
    // =========================================================

    private FoodPassFixture insertFoodPass(
        UUID studentId,
        String status
    ) {

        UUID foodPassId =
            UUID.randomUUID();

        UUID credentialId =
            UUID.randomUUID();

        String rawToken =
            "B8-FOOD-PASS-"
                + UUID.randomUUID();

        String fingerprint =
            tokenHasher.hash(
                rawToken
            );

        jdbcTemplate.update(
            """
            INSERT INTO qr_credentials (
                id,
                credential_type,
                subject_id,
                token_hash,
                status,
                medium
            )
            VALUES (
                ?,
                'FOOD_PASS',
                ?,
                ?,
                'ACTIVE',
                'QR'
            )
            """,
            credentialId,
            foodPassId,
            fingerprint
        );

        jdbcTemplate.update(
            """
            INSERT INTO food_passes (
                id,
                student_id,
                credential_id,
                card_number,
                status
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            foodPassId,
            studentId,
            credentialId,
            "B8-CARD-" + randomSuffix(),
            status
        );

        return new FoodPassFixture(
            foodPassId,
            rawToken
        );
    }

    // =========================================================
    // USAGE FIXTURES / READS
    // =========================================================

    private void insertUsage(
        UUID entitlementId,
        UUID studentId,
        LocalDate date,
        String mealType,
        UUID validatedBy
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO meal_usages (
                id,
                entitlement_id,
                student_id,
                meal_beneficiary_id,
                menu_id,
                usage_date,
                meal_type,
                food_pass_id,
                consumed_at,
                validated_by,
                terminal_id,
                status
            )
            VALUES (
                ?, ?, ?,
                NULL,
                NULL,
                ?, ?,
                NULL,
                CURRENT_TIMESTAMP,
                ?,
                NULL,
                'VALID'
            )
            """,
            UUID.randomUUID(),
            entitlementId,
            studentId,
            date,
            mealType,
            validatedBy
        );
    }

    private long validUsageCount(
        UUID studentId,
        LocalDate date,
        String mealType
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM meal_usages
                WHERE student_id = ?
                  AND usage_date = ?
                  AND meal_type = ?
                  AND status = 'VALID'
                """,
                Long.class,
                studentId,
                date,
                mealType
            );

        return count == null
            ? 0L
            : count;
    }

    private UUID validUsageId(
        UUID studentId,
        LocalDate date,
        String mealType
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM meal_usages
            WHERE student_id = ?
              AND usage_date = ?
              AND meal_type = ?
              AND status = 'VALID'
            """,
            UUID.class,
            studentId,
            date,
            mealType
        );
    }

    private UUID usageReservationId(
        UUID studentId,
        LocalDate date,
        String mealType
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT reservation_id
            FROM meal_usages
            WHERE student_id = ?
              AND usage_date = ?
              AND meal_type = ?
              AND status = 'VALID'
            """,
            UUID.class,
            studentId,
            date,
            mealType
        );
    }

    // =========================================================
    // RESERVATION READS
    // =========================================================

    private UUID reservationId(
        UUID studentId,
        UUID menuId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM canteen_reservations
            WHERE student_id = ?
              AND menu_id = ?
            """,
            UUID.class,
            studentId,
            menuId
        );
    }

    private long reservationCount(
        UUID studentId,
        UUID menuId
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM canteen_reservations
                WHERE student_id = ?
                  AND menu_id = ?
                """,
                Long.class,
                studentId,
                menuId
            );

        return count == null
            ? 0L
            : count;
    }

    private String reservationStatus(
        UUID reservationId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM canteen_reservations
            WHERE id = ?
            """,
            String.class,
            reservationId
        );
    }

    private OffsetDateTime reservationConsumedAt(
        UUID reservationId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT consumed_at
            FROM canteen_reservations
            WHERE id = ?
            """,
            OffsetDateTime.class,
            reservationId
        );
    }

    // =========================================================
    // AUDIT / IDEMPOTENCY
    // =========================================================

    private long auditCount(
        UUID usageId
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM audit_logs
                WHERE action = 'FOOD_PASS_USED'
                  AND resource_type = 'MEAL_USAGE'
                  AND resource_id = ?
                  AND result = 'SUCCESS'
                """,
                Long.class,
                usageId
            );

        return count == null
            ? 0L
            : count;
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

    // =========================================================
    // COMMON
    // =========================================================

    private LocalDate campusToday() {

        return LocalDate.now(
            CAMPUS_ZONE
        );
    }

    private String key(
        String prefix
    ) {

        return "B8-"
            + prefix
            + "-"
            + UUID.randomUUID();
    }

    private String randomSuffix() {

        String raw =
            UUID.randomUUID()
                .toString()
                .replace(
                    "-",
                    ""
                );

        return raw.substring(
            0,
            10
        );
    }

    private record Actor(
        UUID userId,
        UUID studentId,
        String accessToken
    ) {
    }

    private record EntitlementFixture(
        UUID subscriptionId,
        UUID entitlementId,
        UUID planId,
        UUID planVersionId
    ) {
    }

    private record FoodPassFixture(
        UUID foodPassId,
        String rawToken
    ) {
    }

    private record DistributionOutcome(
        boolean success,
        String errorCode,
        UUID usageId
    ) {
    }
}
