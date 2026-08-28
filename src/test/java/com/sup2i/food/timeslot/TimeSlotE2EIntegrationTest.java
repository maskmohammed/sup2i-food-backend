package com.sup2i.food.timeslot;

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
class TimeSlotE2EIntegrationTest {

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

    private Actor studentA;
    private Actor studentB;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "SLOT"
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

        studentA =
            insertActor(
                organizationId,
                campusId,
                "STUA"
            );

        studentB =
            insertActor(
                organizationId,
                campusId,
                "STUB"
            );
    }

    // =========================================================
    // 01 - LISTING WITH REMAINING CAPACITY
    // =========================================================

    @Test
    void listShowsAvailableSlotsWithRemainingCapacity()
        throws Exception {

        UUID slotId =
            insertTimeSlot(
                locationId,
                "12:00",
                "12:15",
                5,
                1
            );

        mockMvc.perform(
                get(
                    "/api/v1/time-slots"
                )
                    .param(
                        "locationId",
                        locationId.toString()
                    )
                    .param(
                        "date",
                        tomorrow()
                    )
                    .header(
                        "Authorization",
                        bearer(studentA)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath(
                    "$[?(@.id=='"
                        + slotId
                        + "')].capacity"
                )
                    .value(5)
            )
            .andExpect(
                jsonPath(
                    "$[?(@.id=='"
                        + slotId
                        + "')].reservedCount"
                )
                    .value(1)
            )
            .andExpect(
                jsonPath(
                    "$[?(@.id=='"
                        + slotId
                        + "')].remainingCapacity"
                )
                    .value(4)
            )
            .andExpect(
                jsonPath(
                    "$[?(@.id=='"
                        + slotId
                        + "')].status"
                )
                    .value("OPEN")
            );
    }

    // =========================================================
    // 02 - RESERVATION DECREMENTS REMAINING CAPACITY
    // =========================================================

    @Test
    void reservingSlotDecrementsRemainingCapacity()
        throws Exception {

        UUID slotId =
            insertTimeSlot(
                locationId,
                "13:00",
                "13:15",
                3,
                0
            );

        UUID orderId =
            createSubmittedOrder(
                studentA,
                slotId,
                "DECR",
                1
            );

        assertThat(
            slotRemainingCapacity(
                slotId
            )
        ).isEqualTo(3);

        beginPayment(
            orderId,
            studentA
        )
            .andExpect(
                status().isOk()
            );

        assertThat(
            slotRemainingCapacity(
                slotId
            )
        ).isEqualTo(2);

        assertThat(
            slotReservedCount(
                slotId
            )
        ).isEqualTo(1);

        assertThat(
            timeSlotReservationStatus(
                orderId
            )
        ).isEqualTo(
            "ACTIVE"
        );
    }

    // =========================================================
    // 03 - CONCURRENT RESERVATIONS ON LAST SLOT
    // =========================================================

    @Test
    void concurrentReservationsOnLastSlotOnlyOneSucceeds()
        throws Exception {

        UUID slotId =
            insertTimeSlot(
                locationId,
                "14:00",
                "14:15",
                1,
                0
            );

        UUID orderIdA =
            createSubmittedOrder(
                studentA,
                slotId,
                "RACEA",
                1
            );

        UUID orderIdB =
            createSubmittedOrder(
                studentB,
                slotId,
                "RACEB",
                1
            );

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        CountDownLatch ready =
            new CountDownLatch(2);

        CountDownLatch start =
            new CountDownLatch(1);

        Callable<Integer> attemptA =
            () -> {
                ready.countDown();
                start.await();
                return beginPayment(
                    orderIdA,
                    studentA
                )
                    .andReturn()
                    .getResponse()
                    .getStatus();
            };

        Callable<Integer> attemptB =
            () -> {
                ready.countDown();
                start.await();
                return beginPayment(
                    orderIdB,
                    studentB
                )
                    .andReturn()
                    .getResponse()
                    .getStatus();
            };

        Future<Integer> futureA =
            executor.submit(attemptA);

        Future<Integer> futureB =
            executor.submit(attemptB);

        ready.await();
        start.countDown();

        int statusA =
            futureA.get();

        int statusB =
            futureB.get();

        executor.shutdown();

        assertThat(
            List.of(statusA, statusB)
        ).containsExactlyInAnyOrder(
            200,
            409
        );

        assertThat(
            slotReservedCount(
                slotId
            )
        ).isEqualTo(1);

        assertThat(
            slotRemainingCapacity(
                slotId
            )
        ).isEqualTo(0);

        assertThat(
            slotStatus(
                slotId
            )
        ).isEqualTo(
            "FULL"
        );
    }

    // =========================================================
    // 04 - FULL SLOT REJECTED
    // =========================================================

    @Test
    void fullSlotIsRejectedCleanly()
        throws Exception {

        UUID slotId =
            insertTimeSlot(
                locationId,
                "15:00",
                "15:15",
                1,
                0
            );

        UUID orderIdA =
            createSubmittedOrder(
                studentA,
                slotId,
                "FULLA",
                1
            );

        beginPayment(
            orderIdA,
            studentA
        )
            .andExpect(
                status().isOk()
            );

        assertThat(
            slotStatus(
                slotId
            )
        ).isEqualTo(
            "FULL"
        );

        UUID orderIdB =
            createSubmittedOrder(
                studentB,
                slotId,
                "FULLB",
                1
            );

        beginPayment(
            orderIdB,
            studentB
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );

        assertThat(
            slotReservedCount(
                slotId
            )
        ).isEqualTo(1);
    }

    // =========================================================
    // 05 - PAST SLOT REJECTED
    // =========================================================

    @Test
    void pastSlotIsRejectedCleanly()
        throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                "PAST",
                "10.00"
            );

        UUID pastSlotId =
            insertPastTimeSlot(
                locationId,
                5
            );

        UUID orderId =
            UUID.randomUUID();

        mockMvc.perform(
                put(
                    "/api/v1/orders/{orderId}",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer(studentA)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "locationId": "%s",
                          "currency": "MAD",
                          "customerNote": "Past slot",
                          "timeSlotId": "%s",
                          "items": [
                            {
                              "productId": "%s",
                              "quantity": 1
                            }
                          ]
                        }
                        """.formatted(
                            locationId,
                            pastSlotId,
                            productId
                        )
                    )
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );
    }

    // =========================================================
    // HTTP HELPERS
    // =========================================================

    private UUID createSubmittedOrder(
        Actor requestActor,
        UUID slotId,
        String prefix,
        int quantity
    ) throws Exception {

        UUID productId =
            insertProduct(
                organizationId,
                prefix,
                "10.00"
            );

        UUID stockItemId =
            insertProductStockItem(
                organizationId,
                productId
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                prefix
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "20.000"
        );

        UUID orderId =
            UUID.randomUUID();

        mockMvc.perform(
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
                    .content(
                        """
                        {
                          "locationId": "%s",
                          "currency": "MAD",
                          "customerNote": "Time slot E2E",
                          "timeSlotId": "%s",
                          "items": [
                            {
                              "productId": "%s",
                              "quantity": %d
                            }
                          ]
                        }
                        """.formatted(
                            locationId,
                            slotId,
                            productId,
                            quantity
                        )
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/submit",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer(requestActor)
                    )
            )
            .andExpect(
                status().isOk()
            );

        return orderId;
    }

    private ResultActions beginPayment(
        UUID orderId,
        Actor requestActor
    ) throws Exception {

        return mockMvc.perform(
            post(
                "/api/v1/orders/{orderId}/begin-payment",
                orderId
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

    private String tomorrow() {

        return java.time.LocalDate.now()
            .plusDays(1)
            .toString();
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
        String prefix
    ) {

        UUID userId =
            UUID.randomUUID();

        String suffix =
            randomSuffix();

        String email =
            "slot-"
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
            "Slot",
            prefix
        );

        UUID studentId =
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
            VALUES (?, ?, ?, ?, 'ACTIVE')
            """,
            studentId,
            userId,
            selectedCampusId,
            "STU-" + randomSuffix()
        );

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "slot-e2e-"
                    + prefix,
                InetAddress
                    .getLoopbackAddress()
            );

        return new Actor(
            userId,
            tokens.accessToken()
        );
    }

    // =========================================================
    // CATALOG / INVENTORY FIXTURES
    // =========================================================

    private UUID insertProduct(
        UUID tenantId,
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
                ?, ?, ?, ?, ?, 'PACKAGED',
                ?, 0.00, 0,
                TRUE, FALSE, TRUE
            )
            """,
            productId,
            tenantId,
            categoryId,
            prefix + "-" + randomSuffix(),
            prefix + " Product",
            new BigDecimal(price)
        );

        return productId;
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

    private void insertBalance(
        UUID stockItemId,
        UUID stockLocationId,
        String physical
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO stock_balances (
                stock_item_id,
                stock_location_id,
                physical_quantity,
                reserved_quantity
            )
            VALUES (?, ?, ?, 0.000)
            """,
            stockItemId,
            stockLocationId,
            new BigDecimal(physical)
        );
    }

    // =========================================================
    // TIME SLOT FIXTURES
    // =========================================================

    private UUID insertTimeSlot(
        UUID selectedLocationId,
        String startTime,
        String endTime,
        int capacity,
        int reservedCount
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
                reserved_count
            )
            VALUES (
                ?, ?,
                CURRENT_DATE + 1,
                ?::time, ?::time,
                ?, ?
            )
            """,
            id,
            selectedLocationId,
            startTime,
            endTime,
            capacity,
            reservedCount
        );

        return id;
    }

    private UUID insertPastTimeSlot(
        UUID selectedLocationId,
        int capacity
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
                reserved_count
            )
            VALUES (
                ?, ?,
                CURRENT_DATE - 1,
                '12:00', '12:15',
                ?, 0
            )
            """,
            id,
            selectedLocationId,
            capacity
        );

        return id;
    }

    // =========================================================
    // DATABASE ASSERTION HELPERS
    // =========================================================

    private int slotReservedCount(
        UUID slotId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT reserved_count
            FROM time_slots
            WHERE id = ?
            """,
            Integer.class,
            slotId
        );
    }

    private int slotRemainingCapacity(
        UUID slotId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT capacity - reserved_count
            FROM time_slots
            WHERE id = ?
            """,
            Integer.class,
            slotId
        );
    }

    private String slotStatus(
        UUID slotId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM time_slots
            WHERE id = ?
            """,
            String.class,
            slotId
        );
    }

    private String timeSlotReservationStatus(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM time_slot_reservations
            WHERE order_id = ?
            """,
            String.class,
            orderId
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
        String accessToken
    ) {
    }
}
