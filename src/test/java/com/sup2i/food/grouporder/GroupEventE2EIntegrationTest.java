package com.sup2i.food.grouporder;

import com.sup2i.food.event.api.dto.CampusEventResponse;
import com.sup2i.food.event.api.dto.CreateCampusEventCommand;
import com.sup2i.food.event.service.CampusEventService;
import com.sup2i.food.eventing.exception.GroupEventConflictException;
import com.sup2i.food.eventing.exception.GroupEventNotFoundException;
import com.sup2i.food.grouporder.api.dto.CreateGroupOrderCommand;
import com.sup2i.food.grouporder.api.dto.GroupOrderMemberResponse;
import com.sup2i.food.grouporder.api.dto.GroupOrderResponse;
import com.sup2i.food.grouporder.domain.GroupOrderMemberStatus;
import com.sup2i.food.grouporder.domain.GroupOrderStatus;
import com.sup2i.food.grouporder.service.GroupOrderService;
import com.sup2i.food.order.api.dto.UpsertOrderItemRequest;
import com.sup2i.food.order.api.dto.UpsertOrderRequest;
import com.sup2i.food.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@ActiveProfiles("test")
@Testcontainers
class GroupEventE2EIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName(
                "sup2i_food_group_event_test"
            );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private GroupOrderService groupOrderService;

    @Autowired
    private CampusEventService campusEventService;

    private UUID organizationId;
    private UUID campusId;
    private UUID locationId;
    private UUID productId;

    private Actor owner;

    @BeforeEach
    void setup() {

        organizationId =
            insertOrganization(
                "B17"
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

        owner =
            insertStudentActor(
                organizationId,
                campusId,
                "OWNER"
            );

        productId =
            insertProduct(
                organizationId,
                "BASE"
            );
    }

    @Test
    void groupCreationPromotesDraftCreatesOwnerAndReplays() {

        UUID orderId =
            createDraft(
                owner,
                locationId,
                productId
            );

        UUID groupId =
            UUID.randomUUID();

        CreateGroupOrderCommand command =
            new CreateGroupOrderCommand(
                joinCode(),
                OffsetDateTime.now()
                    .plusHours(2)
            );

        GroupOrderResponse created =
            groupOrderService.create(
                owner.userId(),
                groupId,
                orderId,
                command
            );

        GroupOrderResponse replay =
            groupOrderService.create(
                owner.userId(),
                groupId,
                orderId,
                command
            );

        assertThat(created.status())
            .isEqualTo(
                GroupOrderStatus.OPEN
            );

        assertThat(created.replayed())
            .isFalse();

        assertThat(replay.replayed())
            .isTrue();

        assertThat(
            orderType(
                orderId
            )
        ).isEqualTo(
            "GROUP_ORDER"
        );

        assertThat(
            memberCount(
                groupId
            )
        ).isEqualTo(1L);

        assertThat(
            memberStatus(
                groupId,
                owner.studentId()
            )
        ).isEqualTo(
            "JOINED"
        );

        assertThatThrownBy(() ->
            groupOrderService.create(
                owner.userId(),
                groupId,
                orderId,
                new CreateGroupOrderCommand(
                    joinCode(),
                    command.closesAt()
                )
            )
        )
            .isInstanceOf(
                GroupEventConflictException.class
            );
    }

    @Test
    void joinedMemberCanClaimItemLeaveAndOwnerCanRemoveAnotherMember() {

        UUID orderId =
            createDraft(
                owner,
                locationId,
                productId
            );

        UUID groupId =
            UUID.randomUUID();

        String code =
            joinCode();

        groupOrderService.create(
            owner.userId(),
            groupId,
            orderId,
            new CreateGroupOrderCommand(
                code,
                OffsetDateTime.now()
                    .plusHours(1)
            )
        );

        Actor member =
            insertStudentActor(
                organizationId,
                campusId,
                "MEMBER"
            );

        GroupOrderMemberResponse joined =
            groupOrderService.join(
                member.userId(),
                code
            );

        assertThat(joined.status())
            .isEqualTo(
                GroupOrderMemberStatus.JOINED
            );

        UUID orderItemId =
            orderItemId(
                orderId
            );

        assertThat(
            groupOrderService.claimItem(
                member.userId(),
                groupId,
                orderItemId
            )
        ).isTrue();

        assertThat(
            itemMemberId(
                orderItemId
            )
        ).isEqualTo(
            joined.id()
        );

        GroupOrderMemberResponse left =
            groupOrderService.leave(
                member.userId(),
                groupId
            );

        assertThat(left.status())
            .isEqualTo(
                GroupOrderMemberStatus.LEFT
            );

        Actor removable =
            insertStudentActor(
                organizationId,
                campusId,
                "REMOVE"
            );

        GroupOrderMemberResponse joinedRemovable =
            groupOrderService.join(
                removable.userId(),
                code
            );

        GroupOrderMemberResponse removed =
            groupOrderService.removeMember(
                owner.userId(),
                groupId,
                joinedRemovable.id()
            );

        assertThat(removed.status())
            .isEqualTo(
                GroupOrderMemberStatus.REMOVED
            );
    }

    @Test
    void lockSubmitAndCompletionReuseCanonicalOrderLifecycle() {

        UUID orderId =
            createDraft(
                owner,
                locationId,
                productId
            );

        UUID groupId =
            UUID.randomUUID();

        groupOrderService.create(
            owner.userId(),
            groupId,
            orderId,
            new CreateGroupOrderCommand(
                joinCode(),
                OffsetDateTime.now()
                    .plusHours(1)
            )
        );

        GroupOrderResponse locked =
            groupOrderService.lock(
                owner.userId(),
                groupId
            );

        assertThat(locked.status())
            .isEqualTo(
                GroupOrderStatus.LOCKED
            );

        GroupOrderResponse submitted =
            groupOrderService.submit(
                owner.userId(),
                groupId
            );

        GroupOrderResponse submitReplay =
            groupOrderService.submit(
                owner.userId(),
                groupId
            );

        assertThat(submitted.status())
            .isEqualTo(
                GroupOrderStatus.SUBMITTED
            );

        assertThat(submitReplay.replayed())
            .isTrue();

        assertThat(
            orderStatus(
                orderId
            )
        ).isEqualTo(
            "CREATED"
        );

        jdbcTemplate.update(
            """
            UPDATE orders
            SET
                status = 'COMPLETED',
                completed_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            orderId
        );

        GroupOrderResponse completed =
            groupOrderService.complete(
                owner.userId(),
                groupId
            );

        GroupOrderResponse completeReplay =
            groupOrderService.complete(
                owner.userId(),
                groupId
            );

        assertThat(completed.status())
            .isEqualTo(
                GroupOrderStatus.COMPLETED
            );

        assertThat(completeReplay.replayed())
            .isTrue();
    }

    @Test
    void groupCancellationDelegatesToCanonicalOrderCancellation() {

        UUID orderId =
            createDraft(
                owner,
                locationId,
                productId
            );

        UUID groupId =
            UUID.randomUUID();

        groupOrderService.create(
            owner.userId(),
            groupId,
            orderId,
            new CreateGroupOrderCommand(
                joinCode(),
                OffsetDateTime.now()
                    .plusHours(1)
            )
        );

        GroupOrderResponse cancelled =
            groupOrderService.cancel(
                owner.userId(),
                groupId
            );

        GroupOrderResponse replay =
            groupOrderService.cancel(
                owner.userId(),
                groupId
            );

        assertThat(cancelled.status())
            .isEqualTo(
                GroupOrderStatus.CANCELLED
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThat(
            orderStatus(
                orderId
            )
        ).isEqualTo(
            "CANCELLED"
        );
    }

    @Test
    void campusEventCreateReplayValidationAndTenantIsolationWork() {

        UUID eventId =
            UUID.randomUUID();

        OffsetDateTime start =
            OffsetDateTime.now()
                .plusDays(1);

        CreateCampusEventCommand command =
            new CreateCampusEventCommand(
                campusId,
                "Forum étudiant",
                "CAMPUS",
                start,
                start.plusHours(4),
                120,
                "B17 event"
            );

        CampusEventResponse created =
            campusEventService.create(
                owner.userId(),
                eventId,
                command
            );

        CampusEventResponse replay =
            campusEventService.create(
                owner.userId(),
                eventId,
                command
            );

        assertThat(created.replayed())
            .isFalse();

        assertThat(replay.replayed())
            .isTrue();

        assertThat(created.campusId())
            .isEqualTo(
                campusId
            );

        assertThat(
            campusEventService.list(
                owner.userId(),
                campusId
            )
        )
            .extracting(
                CampusEventResponse::id
            )
            .contains(
                eventId
            );

        assertThatThrownBy(() ->
            campusEventService.create(
                owner.userId(),
                eventId,
                new CreateCampusEventCommand(
                    campusId,
                    "Different",
                    "CAMPUS",
                    start,
                    start.plusHours(4),
                    120,
                    "B17 event"
                )
            )
        )
            .isInstanceOf(
                GroupEventConflictException.class
            );

        UUID foreignOrganization =
            insertOrganization(
                "FOREIGN-EVENT"
            );

        UUID foreignCampus =
            insertCampus(
                foreignOrganization,
                "FOREIGN"
            );

        Actor foreignActor =
            insertStudentActor(
                foreignOrganization,
                foreignCampus,
                "FOREIGN"
            );

        assertThatThrownBy(() ->
            campusEventService.get(
                foreignActor.userId(),
                eventId
            )
        )
            .isInstanceOf(
                GroupEventNotFoundException.class
            );
    }

    @Test
    void groupJoinIsCampusScopedAndHonorsDatabaseJoinWindow() {

        UUID orderId =
            createDraft(
                owner,
                locationId,
                productId
            );

        UUID groupId =
            UUID.randomUUID();

        String code =
            joinCode();

        groupOrderService.create(
            owner.userId(),
            groupId,
            orderId,
            new CreateGroupOrderCommand(
                code,
                OffsetDateTime.now()
                    .plusHours(1)
            )
        );

        UUID otherCampus =
            insertCampus(
                organizationId,
                "OTHER"
            );

        Actor foreignCampusStudent =
            insertStudentActor(
                organizationId,
                otherCampus,
                "OTHER"
            );

        assertThatThrownBy(() ->
            groupOrderService.join(
                foreignCampusStudent.userId(),
                code
            )
        )
            .isInstanceOf(
                GroupEventNotFoundException.class
            );

        UUID expiredOrder =
            createDraft(
                owner,
                locationId,
                productId
            );

        UUID expiredGroup =
            UUID.randomUUID();

        String expiredCode =
            joinCode();

        groupOrderService.create(
            owner.userId(),
            expiredGroup,
            expiredOrder,
            new CreateGroupOrderCommand(
                expiredCode,
                OffsetDateTime.now()
                    .minusMinutes(1)
            )
        );

        Actor sameCampusStudent =
            insertStudentActor(
                organizationId,
                campusId,
                "LATE"
            );

        assertThatThrownBy(() ->
            groupOrderService.join(
                sameCampusStudent.userId(),
                expiredCode
            )
        )
            .isInstanceOf(
                GroupEventConflictException.class
            )
            .hasMessageContaining(
                "join window"
            );
    }

    @Test
    void concurrentSameStudentJoinSerializesToOneMembership() throws Exception {

        UUID orderId =
            createDraft(
                owner,
                locationId,
                productId
            );

        UUID groupId =
            UUID.randomUUID();

        String code =
            joinCode();

        groupOrderService.create(
            owner.userId(),
            groupId,
            orderId,
            new CreateGroupOrderCommand(
                code,
                OffsetDateTime.now()
                    .plusHours(1)
            )
        );

        Actor member =
            insertStudentActor(
                organizationId,
                campusId,
                "CONCURRENT"
            );

        ExecutorService executor =
            Executors.newFixedThreadPool(
                2
            );

        CountDownLatch start =
            new CountDownLatch(
                1
            );

        try {

            Future<GroupOrderMemberResponse> first =
                executor.submit(() -> {

                    start.await();

                    return groupOrderService.join(
                        member.userId(),
                        code
                    );
                });

            Future<GroupOrderMemberResponse> second =
                executor.submit(() -> {

                    start.await();

                    return groupOrderService.join(
                        member.userId(),
                        code
                    );
                });

            start.countDown();

            GroupOrderMemberResponse firstResult =
                first.get();

            GroupOrderMemberResponse secondResult =
                second.get();

            assertThat(firstResult.status())
                .isEqualTo(
                    GroupOrderMemberStatus.JOINED
                );

            assertThat(secondResult.status())
                .isEqualTo(
                    GroupOrderMemberStatus.JOINED
                );

            long replays =
                (firstResult.replayed() ? 1L : 0L)
                    + (secondResult.replayed() ? 1L : 0L);

            assertThat(replays)
                .isEqualTo(1L);

            assertThat(
                studentMembershipCount(
                    groupId,
                    member.studentId()
                )
            ).isEqualTo(1L);

        } finally {

            executor.shutdownNow();
        }
    }

    private UUID createDraft(
        Actor actor,
        UUID selectedLocationId,
        UUID selectedProductId
    ) {

        UUID orderId =
            UUID.randomUUID();

        orderService.upsertDraft(
            actor.userId(),
            orderId,
            new UpsertOrderRequest(
                selectedLocationId,
                "MAD",
                "B17 Group Order E2E",
                List.of(
                    new UpsertOrderItemRequest(
                        selectedProductId,
                        null,
                        1,
                        "B17"
                    )
                )
            )
        );

        return orderId;
    }

    private UUID insertOrganization(
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO organizations(
                id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, TRUE)
            """,
            id,
            prefix + " Organization",
            prefix + "-" + suffix()
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
            INSERT INTO campuses(
                id,
                organization_id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, ?, TRUE)
            """,
            id,
            tenantId,
            prefix + " Campus",
            "C-" + suffix()
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
            INSERT INTO locations(
                id,
                campus_id,
                name,
                code,
                type,
                is_active
            )
            VALUES (?, ?, ?, ?, 'SNACK', TRUE)
            """,
            id,
            selectedCampusId,
            prefix + " Location",
            "L-" + suffix()
        );

        return id;
    }

    private Actor insertStudentActor(
        UUID tenantId,
        UUID selectedCampusId,
        String prefix
    ) {

        UUID userId =
            UUID.randomUUID();

        UUID studentId =
            UUID.randomUUID();

        String idSuffix =
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO users(
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
            "b17-" +
                prefix.toLowerCase() +
                "-" +
                idSuffix +
                "@sup2i.test",
            "B17",
            prefix
        );

        jdbcTemplate.update(
            """
            INSERT INTO students(
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
            "STU-" + idSuffix
        );

        return new Actor(
            userId,
            studentId
        );
    }

    private UUID insertProduct(
        UUID tenantId,
        String prefix
    ) {

        UUID categoryId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO categories(
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
            "b17-category-" + suffix()
        );

        UUID productId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO products(
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
            "B17-" + suffix(),
            prefix + " Product"
        );

        return productId;
    }

    private String orderType(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT order_type
            FROM orders
            WHERE id = ?
            """,
            String.class,
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

    private UUID orderItemId(
        UUID orderId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM order_items
            WHERE order_id = ?
            ORDER BY id
            LIMIT 1
            """,
            UUID.class,
            orderId
        );
    }

    private UUID itemMemberId(
        UUID orderItemId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT group_order_member_id
            FROM order_items
            WHERE id = ?
            """,
            UUID.class,
            orderItemId
        );
    }

    private Long memberCount(
        UUID groupId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM group_order_members
            WHERE group_order_id = ?
            """,
            Long.class,
            groupId
        );
    }

    private String memberStatus(
        UUID groupId,
        UUID studentId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM group_order_members
            WHERE group_order_id = ?
              AND student_id = ?
            """,
            String.class,
            groupId,
            studentId
        );
    }

    private Long studentMembershipCount(
        UUID groupId,
        UUID studentId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM group_order_members
            WHERE group_order_id = ?
              AND student_id = ?
            """,
            Long.class,
            groupId,
            studentId
        );
    }

    private String joinCode() {

        return "G-" + suffix();
    }

    private String suffix() {

        return UUID.randomUUID()
            .toString()
            .replace(
                "-",
                ""
            )
            .substring(
                0,
                12
            );
    }

    private record Actor(
        UUID userId,
        UUID studentId
    ) {
    }
}