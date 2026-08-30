package com.sup2i.food.slot;

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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
class TimeSlotPreorderE2EIntegrationTest {

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
    private Actor actor;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "B7"
            );

        campusId =
            insertCampus(
                organizationId,
                "MAIN"
            );

        locationId =
            insertLocation(
                campusId,
                "SNACK",
                "SNACK"
            );

        actor =
            insertActor(
                organizationId,
                campusId,
                "STUDENT"
            );
    }

    // =========================================================
    // 01 - LIST / REMAINING / EFFECTIVE CLOSED
    // =========================================================

    @Test
    void listReturnsRemainingCapacityAndClosesPastSlot()
        throws Exception {

        LocalDate futureDate =
            campusToday()
                .plusDays(2);

        UUID futureSlot =
            insertTimeSlot(
                locationId,
                futureDate,
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                3,
                1,
                "OPEN"
            );

        mockMvc.perform(
                get(
                    "/api/v1/slots"
                )
                    .param(
                        "locationId",
                        locationId.toString()
                    )
                    .param(
                        "date",
                        futureDate.toString()
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
                jsonPath("$[0].id")
                    .value(
                        futureSlot.toString()
                    )
            )
            .andExpect(
                jsonPath("$[0].capacity")
                    .value(3)
            )
            .andExpect(
                jsonPath("$[0].reservedCount")
                    .value(1)
            )
            .andExpect(
                jsonPath("$[0].remainingCapacity")
                    .value(2)
            )
            .andExpect(
                jsonPath("$[0].status")
                    .value("OPEN")
            );

        LocalDate pastDate =
            campusToday()
                .minusDays(1);

        insertTimeSlot(
            locationId,
            pastDate,
            LocalTime.of(12, 0),
            LocalTime.of(12, 15),
            3,
            0,
            "OPEN"
        );

        mockMvc.perform(
                get(
                    "/api/v1/slots"
                )
                    .param(
                        "locationId",
                        locationId.toString()
                    )
                    .param(
                        "date",
                        pastDate.toString()
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
                jsonPath("$[0].status")
                    .value("CLOSED")
            );
    }

    // =========================================================
    // 02 - TENANT / LOCATION / FULL / CLOSED
    // =========================================================

    @Test
    void slotSelectionGuardsTenantLocationFullAndClosed()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "GUARD",
                0
            );

        LocalDate date =
            campusToday()
                .plusDays(2);

        UUID otherLocation =
            insertLocation(
                campusId,
                "OTHER",
                "SNACK"
            );

        UUID crossLocationSlot =
            insertTimeSlot(
                otherLocation,
                date,
                LocalTime.of(10, 0),
                LocalTime.of(10, 15),
                2,
                0,
                "OPEN"
            );

        putDraft(
            UUID.randomUUID(),
            actor,
            draftBody(
                locationId,
                crossLocationSlot,
                productId
            )
        )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("SLOT_NOT_FOUND")
            );

        UUID fullSlot =
            insertTimeSlot(
                locationId,
                date,
                LocalTime.of(11, 0),
                LocalTime.of(11, 15),
                1,
                1,
                "FULL"
            );

        putDraft(
            UUID.randomUUID(),
            actor,
            draftBody(
                locationId,
                fullSlot,
                productId
            )
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("SLOT_FULL")
            );

        UUID closedSlot =
            insertTimeSlot(
                locationId,
                date,
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                2,
                0,
                "CLOSED"
            );

        putDraft(
            UUID.randomUUID(),
            actor,
            draftBody(
                locationId,
                closedSlot,
                productId
            )
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("SLOT_CLOSED")
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
                "FOREIGN",
                "SNACK"
            );

        insertTimeSlot(
            foreignLocation,
            date,
            LocalTime.of(13, 0),
            LocalTime.of(13, 15),
            2,
            0,
            "OPEN"
        );

        mockMvc.perform(
                get(
                    "/api/v1/slots"
                )
                    .param(
                        "locationId",
                        foreignLocation.toString()
                    )
                    .param(
                        "date",
                        date.toString()
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
                    .value(0)
            );
    }

    // =========================================================
    // 03 - SUBMIT DURABLE CAPACITY + REPLAY
    // =========================================================

    @Test
    void submitReservesSlotDurablyAndReplayDoesNotDoubleReserve()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "SUBMIT",
                0
            );

        UUID slotId =
            insertTimeSlot(
                locationId,
                campusToday().plusDays(2),
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                2,
                0,
                "OPEN"
            );

        UUID orderId =
            UUID.randomUUID();

        putDraft(
            orderId,
            actor,
            draftBody(
                locationId,
                slotId,
                productId
            )
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.order.slot.id")
                    .value(
                        slotId.toString()
                    )
            );

        assertThat(
            activeSlotReservationCount(
                orderId
            )
        ).isZero();

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
            )
            .andExpect(
                jsonPath("$.order.slot.reservedCount")
                    .value(1)
            );

        assertThat(
            slotReservedCount(
                slotId
            )
        ).isEqualTo(1);

        assertThat(
            activeSlotReservationCount(
                orderId
            )
        ).isEqualTo(1L);

        assertThat(
            slotReservationStatus(
                orderId
            )
        ).isEqualTo(
            "ACTIVE"
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

        assertThat(
            slotReservedCount(
                slotId
            )
        ).isEqualTo(1);

        assertThat(
            slotReservationCount(
                orderId
            )
        ).isEqualTo(1L);
    }

    // =========================================================
    // 04 - CANCEL DURABLE RELEASE + REPLAY
    // =========================================================

    @Test
    void cancelCreatedOrderReleasesSlotOnceAndClosesReservation()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "CANCEL",
                0
            );

        UUID slotId =
            insertTimeSlot(
                locationId,
                campusToday().plusDays(2),
                LocalTime.of(12, 30),
                LocalTime.of(12, 45),
                1,
                0,
                "OPEN"
            );

        UUID orderId =
            createSubmittedSlottedOrder(
                productId,
                slotId
            );

        assertThat(
            slotReservedCount(
                slotId
            )
        ).isEqualTo(1);

        cancel(
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
                    .value("CANCELLED")
            );

        assertThat(
            slotReservedCount(
                slotId
            )
        ).isZero();

        assertThat(
            activeSlotReservationCount(
                orderId
            )
        ).isZero();

        assertThat(
            slotReservationStatus(
                orderId
            )
        ).isEqualTo(
            "CANCELLED"
        );

        assertThat(
            releasedSlotReservationCount(
                orderId
            )
        ).isEqualTo(1L);

        cancel(
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
            slotReservedCount(
                slotId
            )
        ).isZero();

        assertThat(
            slotReservationCount(
                orderId
            )
        ).isEqualTo(1L);
    }

    // =========================================================
    // 05 - FUTURE PAYMENT DEADLINE + EXPIRE STOCK/SLOT
    // =========================================================

    @Test
    void slottedPaymentUsesSlotStartAndExpireReleasesStockAndSlot()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "EXPIRE",
                0
            );

        UUID stockItemId =
            insertProductStockItem(
                organizationId,
                productId
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                "EXPIRE"
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "10.000",
            "0.000"
        );

        LocalDate date =
            campusToday()
                .plusDays(2);

        LocalTime start =
            LocalTime.of(
                15,
                0
            );

        UUID slotId =
            insertTimeSlot(
                locationId,
                date,
                start,
                LocalTime.of(15, 15),
                2,
                0,
                "OPEN"
            );

        UUID orderId =
            createSubmittedSlottedOrder(
                productId,
                slotId
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

        OffsetDateTime actualDeadline =
            paymentDeadline(
                orderId
            );

        OffsetDateTime expectedDeadline =
            ZonedDateTime
                .of(
                    date,
                    start,
                    CAMPUS_ZONE
                )
                .toOffsetDateTime();

        assertThat(
            actualDeadline.toInstant()
        ).isEqualTo(
            expectedDeadline.toInstant()
        );

        assertThat(
            stockReservedQuantity(
                stockItemId,
                stockLocationId
            )
        ).isEqualByComparingTo(
            "1.000"
        );

        jdbcTemplate.update(
            """
            UPDATE orders
            SET payment_expires_at =
                CURRENT_TIMESTAMP
                - INTERVAL '1 minute'
            WHERE id = ?
            """,
            orderId
        );

        expire(
            orderId,
            actor
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.order.status")
                    .value("EXPIRED")
            );

        assertThat(
            stockReservedQuantity(
                stockItemId,
                stockLocationId
            )
        ).isEqualByComparingTo(
            "0.000"
        );

        assertThat(
            slotReservedCount(
                slotId
            )
        ).isZero();

        assertThat(
            slotReservationStatus(
                orderId
            )
        ).isEqualTo(
            "EXPIRED"
        );

        expire(
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
            slotReservationCount(
                orderId
            )
        ).isEqualTo(1L);
    }

    // =========================================================
    // 06 - BUSINESS HOURS + EXCEPTION OVERRIDE
    // =========================================================

    @Test
    void scheduleExceptionOverridesClosedBusinessHours()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "HOURS",
                0
            );

        LocalDate date =
            campusToday()
                .plusDays(3);

        insertBusinessHours(
            locationId,
            date,
            true,
            null,
            null
        );

        insertScheduleException(
            locationId,
            date,
            false,
            LocalTime.of(11, 0),
            LocalTime.of(14, 0)
        );

        UUID allowedSlot =
            insertTimeSlot(
                locationId,
                date,
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                2,
                0,
                "OPEN"
            );

        putDraft(
            UUID.randomUUID(),
            actor,
            draftBody(
                locationId,
                allowedSlot,
                productId
            )
        )
            .andExpect(
                status().isOk()
            );

        jdbcTemplate.update(
            """
            DELETE FROM location_schedule_exceptions
            WHERE location_id = ?
              AND exception_date = ?
            """,
            locationId,
            date
        );

        UUID blockedSlot =
            insertTimeSlot(
                locationId,
                date,
                LocalTime.of(13, 0),
                LocalTime.of(13, 15),
                2,
                0,
                "OPEN"
            );

        putDraft(
            UUID.randomUUID(),
            actor,
            draftBody(
                locationId,
                blockedSlot,
                productId
            )
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("SLOT_CLOSED")
            );
    }

    // =========================================================
    // 07 - ACADEMIC SERVICE CLOSURE
    // =========================================================

    @Test
    void academicClosureBlocksServiceButExamDoesNot()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "CALENDAR",
                0
            );

        LocalDate holidayDate =
            campusToday()
                .plusDays(4);

        LocalDate examDate =
            holidayDate.plusDays(1);

        UUID calendarId =
            insertAcademicCalendar(
                campusId,
                holidayDate,
                examDate
            );

        insertAcademicEvent(
            calendarId,
            "HOLIDAY",
            holidayDate,
            true
        );

        insertAcademicEvent(
            calendarId,
            "EXAM",
            examDate,
            true
        );

        UUID holidaySlot =
            insertTimeSlot(
                locationId,
                holidayDate,
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                2,
                0,
                "OPEN"
            );

        putDraft(
            UUID.randomUUID(),
            actor,
            draftBody(
                locationId,
                holidaySlot,
                productId
            )
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("SLOT_CLOSED")
            );

        UUID examSlot =
            insertTimeSlot(
                locationId,
                examDate,
                LocalTime.of(12, 0),
                LocalTime.of(12, 15),
                2,
                0,
                "OPEN"
            );

        putDraft(
            UUID.randomUUID(),
            actor,
            draftBody(
                locationId,
                examSlot,
                productId
            )
        )
            .andExpect(
                status().isOk()
            );
    }

    // =========================================================
    // 08 - VIRTUAL QUEUE
    // =========================================================

    @Test
    void orderResponseProjectsRealVirtualQueueAndDropsItWhenReady()
        throws Exception {

        UUID kitchenLocationId =
            insertLocation(
                campusId,
                "KITCHEN",
                "KITCHEN"
            );

        UUID aheadProductA =
            insertProduct(
                organizationId,
                "QUEUE-A",
                6
            );

        UUID aheadProductB =
            insertProduct(
                organizationId,
                "QUEUE-B",
                4
            );

        UUID targetProduct =
            insertProduct(
                organizationId,
                "QUEUE-T",
                8
            );

        OffsetDateTime now =
            OffsetDateTime.now();

        insertQueuedOrder(
            aheadProductA,
            kitchenLocationId,
            2,
            now.minusMinutes(10)
        );

        insertQueuedOrder(
            aheadProductB,
            kitchenLocationId,
            1,
            now.minusMinutes(5)
        );

        UUID targetOrder =
            insertQueuedOrder(
                targetProduct,
                kitchenLocationId,
                1,
                now.minusMinutes(1)
            );

        mockMvc.perform(
                get(
                    "/api/v1/orders/{orderId}",
                    targetOrder
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
                jsonPath("$.status")
                    .value("QUEUED")
            )
            .andExpect(
                jsonPath("$.queue.ordersAhead")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.queue.estimatedMinutes")
                    .value(18)
            );

        jdbcTemplate.update(
            """
            UPDATE orders
            SET status = 'READY',
                ready_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            targetOrder
        );

        mockMvc.perform(
                get(
                    "/api/v1/orders/{orderId}",
                    targetOrder
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
                jsonPath("$.status")
                    .value("READY")
            )
            .andExpect(
                jsonPath("$.queue")
                    .doesNotExist()
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

    private UUID createSubmittedSlottedOrder(
        UUID productId,
        UUID slotId
    ) throws Exception {

        UUID orderId =
            UUID.randomUUID();

        putDraft(
            orderId,
            actor,
            draftBody(
                locationId,
                slotId,
                productId
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

    private String draftBody(
        UUID selectedLocationId,
        UUID slotId,
        UUID productId
    ) {

        return """
            {
              "locationId": "%s",
              "slotId": "%s",
              "currency": "MAD",
              "customerNote": "B7 preorder E2E",
              "items": [
                {
                  "productId": "%s",
                  "quantity": 1,
                  "specialInstructions": "B7"
                }
              ]
            }
            """.formatted(
                selectedLocationId,
                slotId,
                productId
            );
    }

    // =========================================================
    // TENANT / IDENTITY
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
            prefix + suffix
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
            "C" + randomSuffix()
        );

        return id;
    }

    private UUID insertLocation(
        UUID selectedCampusId,
        String prefix,
        String type
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
            VALUES (?, ?, ?, ?, ?, TRUE)
            """,
            id,
            selectedCampusId,
            prefix + " Location",
            "L" + randomSuffix(),
            type
        );

        return id;
    }

    private Actor insertActor(
        UUID tenantId,
        UUID selectedCampusId,
        String prefix
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
            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """,
            userId,
            tenantId,
            "b7-" + suffix + "@sup2i.test",
            "B7",
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
            VALUES (?, ?, ?, ?, 'ACTIVE')
            """,
            studentId,
            userId,
            selectedCampusId,
            "B7-" + suffix
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "b7-e2e",
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
    // SLOT FIXTURES
    // =========================================================

    private UUID insertTimeSlot(
        UUID selectedLocationId,
        LocalDate date,
        LocalTime start,
        LocalTime end,
        int capacity,
        int reservedCount,
        String slotStatus
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO time_slots (
                id,
                location_id,
                slot_date,
                start_time,
                end_time,
                capacity,
                reserved_count,
                status
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            selectedLocationId,
            date,
            start,
            end,
            capacity,
            reservedCount,
            slotStatus
        );

        return id;
    }

    private int slotReservedCount(
        UUID slotId
    ) {

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT reserved_count
                FROM time_slots
                WHERE id = ?
                """,
                Integer.class,
                slotId
            );

        return count;
    }

    private Long activeSlotReservationCount(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM time_slot_reservations
            WHERE order_id = ?
              AND status = 'ACTIVE'
            """,
            Long.class,
            orderId
        );
    }

    private Long slotReservationCount(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM time_slot_reservations
            WHERE order_id = ?
            """,
            Long.class,
            orderId
        );
    }

    private Long releasedSlotReservationCount(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM time_slot_reservations
            WHERE order_id = ?
              AND released_at IS NOT NULL
            """,
            Long.class,
            orderId
        );
    }

    private String slotReservationStatus(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM time_slot_reservations
            WHERE order_id = ?
            ORDER BY reserved_at DESC
            LIMIT 1
            """,
            String.class,
            orderId
        );
    }

    // =========================================================
    // POLICY FIXTURES
    // =========================================================

    private void insertBusinessHours(
        UUID selectedLocationId,
        LocalDate date,
        boolean closed,
        LocalTime opensAt,
        LocalTime closesAt
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO location_business_hours (
                id,
                location_id,
                day_of_week,
                opens_at,
                closes_at,
                is_closed,
                valid_from,
                valid_to
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            selectedLocationId,
            date.getDayOfWeek().getValue(),
            opensAt,
            closesAt,
            closed,
            date,
            date
        );
    }

    private void insertScheduleException(
        UUID selectedLocationId,
        LocalDate date,
        boolean closed,
        LocalTime opensAt,
        LocalTime closesAt
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO location_schedule_exceptions (
                id,
                location_id,
                exception_date,
                is_closed,
                opens_at,
                closes_at,
                reason
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            selectedLocationId,
            date,
            closed,
            opensAt,
            closesAt,
            "B7 E2E"
        );
    }

    private UUID insertAcademicCalendar(
        UUID selectedCampusId,
        LocalDate startsOn,
        LocalDate endsOn
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO academic_calendars (
                id,
                campus_id,
                name,
                starts_on,
                ends_on,
                is_active
            )
            VALUES (?, ?, ?, ?, ?, TRUE)
            """,
            id,
            selectedCampusId,
            "B7 Calendar " + randomSuffix(),
            startsOn,
            endsOn
        );

        return id;
    }

    private void insertAcademicEvent(
        UUID calendarId,
        String eventType,
        LocalDate date,
        boolean affectsService
    ) {

        OffsetDateTime startsAt =
            ZonedDateTime
                .of(
                    date,
                    LocalTime.of(11, 0),
                    CAMPUS_ZONE
                )
                .toOffsetDateTime();

        OffsetDateTime endsAt =
            ZonedDateTime
                .of(
                    date,
                    LocalTime.of(13, 0),
                    CAMPUS_ZONE
                )
                .toOffsetDateTime();

        jdbcTemplate.update(
            """
            INSERT INTO academic_calendar_events (
                id,
                academic_calendar_id,
                event_type,
                title,
                starts_at,
                ends_at,
                affects_service
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            calendarId,
            eventType,
            "B7 " + eventType,
            startsAt,
            endsAt,
            affectsService
        );
    }

    // =========================================================
    // CATALOG / STOCK
    // =========================================================

    private UUID insertProduct(
        UUID tenantId,
        String prefix,
        int preparationMinutes
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
            "b7-" + randomSuffix()
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
                ?,
                TRUE,
                FALSE,
                TRUE
            )
            """,
            productId,
            tenantId,
            categoryId,
            prefix + "-" + randomSuffix(),
            prefix + " Product",
            preparationMinutes
        );

        return productId;
    }

    private UUID insertProductStockItem(
        UUID tenantId,
        UUID productId
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
            VALUES (?, ?, ?, 'PIECE', FALSE)
            """,
            id,
            tenantId,
            productId
        );

        return id;
    }

    private UUID insertStockLocation(
        UUID selectedLocationId,
        String prefix
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
            VALUES (?, ?, ?, 'STORAGE', TRUE)
            """,
            id,
            selectedLocationId,
            prefix + " Stock"
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
            new BigDecimal(physical),
            new BigDecimal(reserved)
        );
    }

    private BigDecimal stockReservedQuantity(
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

    private OffsetDateTime paymentDeadline(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT payment_expires_at
            FROM orders
            WHERE id = ?
            """,
            OffsetDateTime.class,
            orderId
        );
    }

    // =========================================================
    // QUEUE FIXTURE
    // =========================================================

    private UUID insertQueuedOrder(
        UUID productId,
        UUID kitchenLocationId,
        int priority,
        OffsetDateTime queuedAt
    ) {

        UUID orderId =
            UUID.randomUUID();

        UUID orderItemId =
            UUID.randomUUID();

        UUID ticketId =
            UUID.randomUUID();

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
                order_type,
                payment_status,
                tax_total
            )
            VALUES (
                ?, ?, ?, ?, ?,
                ?, CURRENT_DATE,
                'MOBILE',
                'QUEUED',
                10.00,
                0.00,
                10.00,
                'MAD',
                'MOBILE_SNACK',
                'COMPLETED',
                0.00
            )
            """,
            orderId,
            organizationId,
            campusId,
            locationId,
            actor.studentId(),
            "B7Q-" + randomSuffix()
        );

        jdbcTemplate.update(
            """
            INSERT INTO order_items (
                id,
                order_id,
                product_id,
                product_name_snapshot,
                unit_price,
                quantity,
                discount_amount,
                line_total
            )
            VALUES (
                ?, ?, ?,
                'B7 Queue Product',
                10.00,
                1,
                0.00,
                10.00
            )
            """,
            orderItemId,
            orderId,
            productId
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
                ?, ?, ?,
                'QUEUED',
                ?,
                ?
            )
            """,
            ticketId,
            orderId,
            kitchenLocationId,
            priority,
            queuedAt
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
                ?, ?, ?, NULL,
                1.000,
                'QUEUED'
            )
            """,
            UUID.randomUUID(),
            ticketId,
            orderItemId
        );

        return orderId;
    }

    private LocalDate campusToday() {

        return LocalDate.now(
            CAMPUS_ZONE
        );
    }

    private String randomSuffix() {

        return UUID
            .randomUUID()
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

    private record Actor(
        UUID userId,
        UUID studentId,
        String accessToken
    ) {
    }
}