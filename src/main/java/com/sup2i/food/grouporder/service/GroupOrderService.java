package com.sup2i.food.grouporder.service;

import com.sup2i.food.eventing.exception.GroupEventConflictException;
import com.sup2i.food.eventing.exception.GroupEventNotFoundException;
import com.sup2i.food.eventing.exception.GroupEventValidationException;
import com.sup2i.food.grouporder.api.dto.CreateGroupOrderCommand;
import com.sup2i.food.grouporder.api.dto.GroupOrderMemberResponse;
import com.sup2i.food.grouporder.api.dto.GroupOrderResponse;
import com.sup2i.food.grouporder.domain.GroupOrderMemberStatus;
import com.sup2i.food.grouporder.domain.GroupOrderStatus;
import com.sup2i.food.order.service.OrderService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GroupOrderService {

    private final JdbcTemplate jdbcTemplate;
    private final OrderService orderService;

    public GroupOrderService(
        JdbcTemplate jdbcTemplate,
        OrderService orderService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderService = orderService;
    }

    @Transactional
    public GroupOrderResponse create(
        UUID actorId,
        UUID groupOrderId,
        UUID orderId,
        CreateGroupOrderCommand command
    ) {

        StudentContext actor =
            lockedStudentActor(actorId);

        requireId(groupOrderId, "Group order id");
        requireId(orderId, "Order id");
        validateCreate(command);

        String joinCode =
            normalizedJoinCode(command.joinCode());

        GroupOrderResponse existing =
            findGroupById(
                actor.organizationId(),
                groupOrderId,
                false
            );

        if (existing != null) {

            boolean same =
                existing.orderId().equals(orderId)
                    && existing.ownerStudentId().equals(actor.studentId())
                    && existing.joinCode().equals(joinCode)
                    && samePostgresTimestamp(existing.closesAt(), command.closesAt());

            if (same) {
                return replayGroup(existing);
            }

            throw new GroupEventConflictException(
                "Group order identifier is already used by another payload."
            );
        }

        OrderContext order =
            lockedOrder(
                actor.organizationId(),
                orderId
            );

        requireSameCampus(
            actor,
            order.campusId()
        );

        if (!actor.studentId().equals(order.studentId())) {
            throw new GroupEventNotFoundException(
                "Order does not exist for authenticated student."
            );
        }

        if (!"MOBILE".equals(order.source())) {
            throw new GroupEventConflictException(
                "Group order root must be a MOBILE student order."
            );
        }

        if (!"DRAFT".equals(order.status())) {
            throw new GroupEventConflictException(
                "Only a DRAFT order can become a group order."
            );
        }

        boolean promotable =
            "MOBILE_SNACK".equals(order.orderType())
                || "GROUP_ORDER".equals(order.orderType());

        if (!promotable) {
            throw new GroupEventConflictException(
                "Order type cannot be promoted to GROUP_ORDER."
            );
        }

        GroupOrderResponse byOrder =
            findGroupByOrder(
                actor.organizationId(),
                orderId,
                true
            );

        if (byOrder != null) {
            throw new GroupEventConflictException(
                "Order already belongs to another group order."
            );
        }

        GroupOrderResponse byCode =
            findGroupByJoinCode(
                actor.organizationId(),
                joinCode,
                true
            );

        if (byCode != null) {
            throw new GroupEventConflictException(
                "Group join code is already in use."
            );
        }

        int promoted =
            jdbcTemplate.update(
                """
                UPDATE orders
                SET order_type = 'GROUP_ORDER'
                WHERE id = ?
                  AND organization_id = ?
                  AND student_id = ?
                  AND source = 'MOBILE'
                  AND status = 'DRAFT'
                  AND order_type IN ('MOBILE_SNACK','GROUP_ORDER')
                """,
                orderId,
                actor.organizationId(),
                actor.studentId()
            );

        if (promoted != 1) {
            throw new GroupEventConflictException(
                "Order changed concurrently while creating group order."
            );
        }

        try {

            jdbcTemplate.update(
                """
                INSERT INTO group_orders(
                    id,
                    order_id,
                    owner_student_id,
                    join_code,
                    status,
                    closes_at
                )
                VALUES (?, ?, ?, ?, 'OPEN', ?)
                """,
                groupOrderId,
                orderId,
                actor.studentId(),
                joinCode,
                command.closesAt()
            );

            jdbcTemplate.update(
                """
                INSERT INTO group_order_members(
                    id,
                    group_order_id,
                    student_id,
                    status
                )
                VALUES (?, ?, ?, 'JOINED')
                """,
                UUID.randomUUID(),
                groupOrderId,
                actor.studentId()
            );

        } catch (DataIntegrityViolationException exception) {

            throw new GroupEventConflictException(
                "Group order conflicts with an existing resource."
            );
        }

        return get(
            actorId,
            groupOrderId
        );
    }

    @Transactional
    public GroupOrderMemberResponse join(
        UUID actorId,
        String joinCode
    ) {

        StudentContext actor =
            lockedStudentActor(actorId);

        String normalizedCode =
            normalizedJoinCode(joinCode);

        GroupContext group =
            lockedGroupByJoinCode(
                actor.organizationId(),
                normalizedCode
            );

        requireSameCampus(
            actor,
            group.campusId()
        );

        GroupOrderMemberResponse existing =
            findMember(
                group.id(),
                actor.studentId(),
                true
            );

        if (existing != null) {

            if (existing.status() == GroupOrderMemberStatus.JOINED) {
                return replayMember(existing);
            }

            throw new GroupEventConflictException(
                "Student membership already exists and cannot be rejoined automatically."
            );
        }

        requireOpenForMembership(group);

        UUID memberId =
            UUID.randomUUID();

        int inserted;

        try {

            inserted =
                jdbcTemplate.update(
                    """
                    INSERT INTO group_order_members(
                        id,
                        group_order_id,
                        student_id,
                        status
                    )
                    VALUES (?, ?, ?, 'JOINED')
                    ON CONFLICT (
                        group_order_id,
                        student_id
                    )
                    DO NOTHING
                    """,
                    memberId,
                    group.id(),
                    actor.studentId()
                );

        } catch (DataIntegrityViolationException exception) {

            throw new GroupEventConflictException(
                "Group membership conflicts with an existing resource."
            );
        }

        if (inserted == 0) {

            GroupOrderMemberResponse replay =
                findMember(
                    group.id(),
                    actor.studentId(),
                    false
                );

            if (
                replay != null
                    && replay.status()
                    == GroupOrderMemberStatus.JOINED
            ) {
                return replayMember(replay);
            }

            throw new GroupEventConflictException(
                "Group membership conflicts with an existing resource."
            );
        }

        return findMemberRequired(
            group.id(),
            actor.studentId()
        );
    }

    @Transactional
    public GroupOrderMemberResponse leave(
        UUID actorId,
        UUID groupOrderId
    ) {

        StudentContext actor =
            lockedStudentActor(actorId);

        GroupContext group =
            lockedGroupById(
                actor.organizationId(),
                groupOrderId
            );

        requireSameCampus(
            actor,
            group.campusId()
        );

        if (group.ownerStudentId().equals(actor.studentId())) {
            throw new GroupEventConflictException(
                "Group owner cannot leave the group order."
            );
        }

        GroupOrderMemberResponse member =
            findMemberRequiredForUpdate(
                group.id(),
                actor.studentId()
            );

        if (member.status() == GroupOrderMemberStatus.LEFT) {
            return replayMember(member);
        }

        if (member.status() != GroupOrderMemberStatus.JOINED) {
            throw new GroupEventConflictException(
                "Only a JOINED member can leave."
            );
        }

        if (group.status() != GroupOrderStatus.OPEN) {
            throw new GroupEventConflictException(
                "Members can leave only while group order is OPEN."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE group_order_members
                SET
                    status = 'LEFT',
                    left_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'JOINED'
                """,
                member.id()
            );

        if (updated != 1) {
            throw new GroupEventConflictException(
                "Group membership changed concurrently."
            );
        }

        return findMemberRequired(
            group.id(),
            actor.studentId()
        );
    }

    @Transactional
    public GroupOrderMemberResponse removeMember(
        UUID actorId,
        UUID groupOrderId,
        UUID memberId
    ) {

        StudentContext actor =
            lockedStudentActor(actorId);

        requireId(
            memberId,
            "Member id"
        );

        GroupContext group =
            lockedOwnedGroup(
                actor,
                groupOrderId
            );

        GroupOrderMemberResponse member =
            lockedMemberById(
                group.id(),
                memberId
            );

        if (member.studentId().equals(group.ownerStudentId())) {
            throw new GroupEventConflictException(
                "Group owner cannot be removed."
            );
        }

        if (member.status() == GroupOrderMemberStatus.REMOVED) {
            return replayMember(member);
        }

        if (group.status() != GroupOrderStatus.OPEN) {
            throw new GroupEventConflictException(
                "Members can be removed only while group order is OPEN."
            );
        }

        boolean removable =
            member.status() == GroupOrderMemberStatus.JOINED
                || member.status() == GroupOrderMemberStatus.INVITED;

        if (!removable) {
            throw new GroupEventConflictException(
                "Member cannot be removed from current state."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE group_order_members
                SET
                    status = 'REMOVED',
                    left_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                member.id()
            );

        if (updated != 1) {
            throw new GroupEventConflictException(
                "Group membership changed concurrently."
            );
        }

        return lockedMemberById(
            group.id(),
            member.id()
        );
    }

    @Transactional
    public boolean claimItem(
        UUID actorId,
        UUID groupOrderId,
        UUID orderItemId
    ) {

        StudentContext actor =
            lockedStudentActor(actorId);

        requireId(
            orderItemId,
            "Order item id"
        );

        GroupContext group =
            lockedGroupById(
                actor.organizationId(),
                groupOrderId
            );

        requireSameCampus(
            actor,
            group.campusId()
        );

        GroupOrderMemberResponse member =
            findMemberRequiredForUpdate(
                group.id(),
                actor.studentId()
            );

        if (member.status() != GroupOrderMemberStatus.JOINED) {
            throw new GroupEventConflictException(
                "Only a JOINED member can claim a group order item."
            );
        }

        ItemContext item =
            lockedOrderItem(
                group.orderId(),
                orderItemId
            );

        if (member.id().equals(item.groupOrderMemberId())) {
            return true;
        }

        if (item.groupOrderMemberId() != null) {
            throw new GroupEventConflictException(
                "Order item already belongs to another group member."
            );
        }

        if (group.status() != GroupOrderStatus.OPEN) {
            throw new GroupEventConflictException(
                "Order items can be attributed only while group order is OPEN."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE order_items
                SET group_order_member_id = ?
                WHERE id = ?
                  AND order_id = ?
                  AND group_order_member_id IS NULL
                """,
                member.id(),
                orderItemId,
                group.orderId()
            );

        if (updated != 1) {
            throw new GroupEventConflictException(
                "Order item changed concurrently."
            );
        }

        return true;
    }

    @Transactional
    public GroupOrderResponse lock(
        UUID actorId,
        UUID groupOrderId
    ) {

        StudentContext actor =
            lockedStudentActor(actorId);

        GroupContext group =
            lockedOwnedGroup(
                actor,
                groupOrderId
            );

        if (group.status() == GroupOrderStatus.LOCKED) {

            return replayGroup(
                getByContext(
                    actor.organizationId(),
                    groupOrderId
                )
            );
        }

        if (group.status() != GroupOrderStatus.OPEN) {
            throw new GroupEventConflictException(
                "Only an OPEN group order can be LOCKED."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE group_orders
                SET status = 'LOCKED'
                WHERE id = ?
                  AND status = 'OPEN'
                """,
                group.id()
            );

        if (updated != 1) {
            throw new GroupEventConflictException(
                "Group order changed concurrently."
            );
        }

        return getByContext(
            actor.organizationId(),
            group.id()
        );
    }

    @Transactional
    public GroupOrderResponse submit(
        UUID actorId,
        UUID groupOrderId
    ) {

        StudentContext actor =
            lockedStudentActor(actorId);

        GroupContext group =
            lockedOwnedGroup(
                actor,
                groupOrderId
            );

        if (group.status() == GroupOrderStatus.SUBMITTED) {

            return replayGroup(
                getByContext(
                    actor.organizationId(),
                    group.id()
                )
            );
        }

        if (group.status() != GroupOrderStatus.LOCKED) {
            throw new GroupEventConflictException(
                "Only a LOCKED group order can be SUBMITTED."
            );
        }

        orderService.submit(
            actorId,
            group.orderId()
        );

        int updated =
            jdbcTemplate.update(
                """
                UPDATE group_orders
                SET status = 'SUBMITTED'
                WHERE id = ?
                  AND status = 'LOCKED'
                """,
                group.id()
            );

        if (updated != 1) {
            throw new GroupEventConflictException(
                "Group order changed concurrently."
            );
        }

        return getByContext(
            actor.organizationId(),
            group.id()
        );
    }

    @Transactional
    public GroupOrderResponse cancel(
        UUID actorId,
        UUID groupOrderId
    ) {

        StudentContext actor =
            lockedStudentActor(actorId);

        GroupContext group =
            lockedOwnedGroup(
                actor,
                groupOrderId
            );

        if (group.status() == GroupOrderStatus.CANCELLED) {

            return replayGroup(
                getByContext(
                    actor.organizationId(),
                    group.id()
                )
            );
        }

        if (group.status() == GroupOrderStatus.COMPLETED) {
            throw new GroupEventConflictException(
                "Completed group order cannot be cancelled."
            );
        }

        orderService.cancel(
            actorId,
            group.orderId()
        );

        int updated =
            jdbcTemplate.update(
                """
                UPDATE group_orders
                SET status = 'CANCELLED'
                WHERE id = ?
                  AND status <> 'COMPLETED'
                """,
                group.id()
            );

        if (updated != 1) {
            throw new GroupEventConflictException(
                "Group order changed concurrently."
            );
        }

        return getByContext(
            actor.organizationId(),
            group.id()
        );
    }

    @Transactional
    public GroupOrderResponse complete(
        UUID actorId,
        UUID groupOrderId
    ) {

        StudentContext actor =
            lockedStudentActor(actorId);

        GroupContext group =
            lockedOwnedGroup(
                actor,
                groupOrderId
            );

        if (group.status() == GroupOrderStatus.COMPLETED) {

            return replayGroup(
                getByContext(
                    actor.organizationId(),
                    group.id()
                )
            );
        }

        if (group.status() != GroupOrderStatus.SUBMITTED) {
            throw new GroupEventConflictException(
                "Only a SUBMITTED group order can be completed."
            );
        }

        String orderStatus =
            jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM orders
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                String.class,
                group.orderId(),
                actor.organizationId()
            );

        if (!"COMPLETED".equals(orderStatus)) {
            throw new GroupEventConflictException(
                "Linked order must be COMPLETED first."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE group_orders
                SET status = 'COMPLETED'
                WHERE id = ?
                  AND status = 'SUBMITTED'
                """,
                group.id()
            );

        if (updated != 1) {
            throw new GroupEventConflictException(
                "Group order changed concurrently."
            );
        }

        return getByContext(
            actor.organizationId(),
            group.id()
        );
    }

    @Transactional(readOnly = true)
    public GroupOrderResponse get(
        UUID actorId,
        UUID groupOrderId
    ) {

        StudentContext actor =
            studentActor(actorId);

        GroupOrderResponse group =
            getByContext(
                actor.organizationId(),
                groupOrderId
            );

        UUID campusId =
            campusForGroup(
                actor.organizationId(),
                groupOrderId
            );

        requireSameCampus(
            actor,
            campusId
        );

        return group;
    }

    @Transactional(readOnly = true)
    public List<GroupOrderMemberResponse> listMembers(
        UUID actorId,
        UUID groupOrderId
    ) {

        StudentContext actor =
            studentActor(actorId);

        GroupOrderResponse group =
            getByContext(
                actor.organizationId(),
                groupOrderId
            );

        UUID campusId =
            campusForGroup(
                actor.organizationId(),
                group.id()
            );

        requireSameCampus(
            actor,
            campusId
        );

        return jdbcTemplate.query(
            """
            SELECT
                id,
                group_order_id,
                student_id,
                status,
                joined_at,
                left_at
            FROM group_order_members
            WHERE group_order_id = ?
            ORDER BY joined_at ASC, id ASC
            """,
            (resultSet, rowNumber) ->
                memberResponse(
                    resultSet,
                    false
                ),
            groupOrderId
        );
    }

    private GroupContext lockedOwnedGroup(
        StudentContext actor,
        UUID groupOrderId
    ) {

        GroupContext group =
            lockedGroupById(
                actor.organizationId(),
                groupOrderId
            );

        requireSameCampus(
            actor,
            group.campusId()
        );

        if (!group.ownerStudentId().equals(actor.studentId())) {

            throw new GroupEventNotFoundException(
                "Group order does not exist for authenticated owner."
            );
        }

        return group;
    }

    private GroupContext lockedGroupById(
        UUID organizationId,
        UUID groupOrderId
    ) {

        requireId(
            groupOrderId,
            "Group order id"
        );

        List<GroupContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    g.id,
                    g.order_id,
                    g.owner_student_id,
                    g.join_code,
                    g.status,
                    g.closes_at,
                    o.campus_id,
                    CURRENT_TIMESTAMP AS database_now
                FROM group_orders g
                JOIN orders o
                  ON o.id = g.order_id
                WHERE g.id = ?
                  AND o.organization_id = ?
                FOR UPDATE OF g
                """,
                (resultSet, rowNumber) ->
                    groupContext(resultSet),
                groupOrderId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new GroupEventNotFoundException(
                "Group order does not exist."
            );
        }

        return rows.get(0);
    }

    private GroupContext lockedGroupByJoinCode(
        UUID organizationId,
        String joinCode
    ) {

        List<GroupContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    g.id,
                    g.order_id,
                    g.owner_student_id,
                    g.join_code,
                    g.status,
                    g.closes_at,
                    o.campus_id,
                    CURRENT_TIMESTAMP AS database_now
                FROM group_orders g
                JOIN orders o
                  ON o.id = g.order_id
                WHERE g.join_code = ?
                  AND o.organization_id = ?
                FOR UPDATE OF g
                """,
                (resultSet, rowNumber) ->
                    groupContext(resultSet),
                joinCode,
                organizationId
            );

        if (rows.size() != 1) {
            throw new GroupEventNotFoundException(
                "Group order does not exist."
            );
        }

        return rows.get(0);
    }

    private void requireOpenForMembership(
        GroupContext group
    ) {

        if (group.status() != GroupOrderStatus.OPEN) {
            throw new GroupEventConflictException(
                "Group order is not OPEN."
            );
        }

        boolean closedByTime =
            group.closesAt() != null
                && !group.databaseNow().isBefore(group.closesAt());

        if (closedByTime) {
            throw new GroupEventConflictException(
                "Group order join window is closed."
            );
        }
    }

    private GroupOrderResponse findGroupById(
        UUID organizationId,
        UUID groupOrderId,
        boolean lock
    ) {

        String sql =
            """
            SELECT
                g.id,
                g.order_id,
                g.owner_student_id,
                g.join_code,
                g.status,
                g.closes_at,
                g.created_at
            FROM group_orders g
            JOIN orders o
              ON o.id = g.order_id
            WHERE g.id = ?
              AND o.organization_id = ?
            """;

        if (lock) {
            sql = sql + " FOR UPDATE OF g";
        }

        List<GroupOrderResponse> rows =
            jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                    groupResponse(
                        resultSet,
                        false
                    ),
                groupOrderId,
                organizationId
            );

        return singleGroup(rows);
    }

    private GroupOrderResponse findGroupByOrder(
        UUID organizationId,
        UUID orderId,
        boolean lock
    ) {

        String sql =
            """
            SELECT
                g.id,
                g.order_id,
                g.owner_student_id,
                g.join_code,
                g.status,
                g.closes_at,
                g.created_at
            FROM group_orders g
            JOIN orders o
              ON o.id = g.order_id
            WHERE g.order_id = ?
              AND o.organization_id = ?
            """;

        if (lock) {
            sql = sql + " FOR UPDATE OF g";
        }

        List<GroupOrderResponse> rows =
            jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                    groupResponse(
                        resultSet,
                        false
                    ),
                orderId,
                organizationId
            );

        return singleGroup(rows);
    }

    private GroupOrderResponse findGroupByJoinCode(
        UUID organizationId,
        String joinCode,
        boolean lock
    ) {

        String sql =
            """
            SELECT
                g.id,
                g.order_id,
                g.owner_student_id,
                g.join_code,
                g.status,
                g.closes_at,
                g.created_at
            FROM group_orders g
            JOIN orders o
              ON o.id = g.order_id
            WHERE g.join_code = ?
              AND o.organization_id = ?
            """;

        if (lock) {
            sql = sql + " FOR UPDATE OF g";
        }

        List<GroupOrderResponse> rows =
            jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                    groupResponse(
                        resultSet,
                        false
                    ),
                joinCode,
                organizationId
            );

        return singleGroup(rows);
    }

    private GroupOrderResponse getByContext(
        UUID organizationId,
        UUID groupOrderId
    ) {

        GroupOrderResponse group =
            findGroupById(
                organizationId,
                groupOrderId,
                false
            );

        if (group == null) {
            throw new GroupEventNotFoundException(
                "Group order does not exist."
            );
        }

        return group;
    }

    private GroupOrderResponse singleGroup(
        List<GroupOrderResponse> rows
    ) {

        if (rows.size() > 1) {
            throw new GroupEventConflictException(
                "Multiple group orders matched one identifier."
            );
        }

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private GroupOrderMemberResponse findMember(
        UUID groupOrderId,
        UUID studentId,
        boolean lock
    ) {

        String sql =
            """
            SELECT
                id,
                group_order_id,
                student_id,
                status,
                joined_at,
                left_at
            FROM group_order_members
            WHERE group_order_id = ?
              AND student_id = ?
            """;

        if (lock) {
            sql = sql + " FOR UPDATE";
        }

        List<GroupOrderMemberResponse> rows =
            jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                    memberResponse(
                        resultSet,
                        false
                    ),
                groupOrderId,
                studentId
            );

        if (rows.size() > 1) {
            throw new GroupEventConflictException(
                "Multiple memberships matched one student."
            );
        }

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private GroupOrderMemberResponse findMemberRequired(
        UUID groupOrderId,
        UUID studentId
    ) {

        GroupOrderMemberResponse member =
            findMember(
                groupOrderId,
                studentId,
                false
            );

        if (member == null) {
            throw new GroupEventNotFoundException(
                "Group membership does not exist."
            );
        }

        return member;
    }

    private GroupOrderMemberResponse findMemberRequiredForUpdate(
        UUID groupOrderId,
        UUID studentId
    ) {

        GroupOrderMemberResponse member =
            findMember(
                groupOrderId,
                studentId,
                true
            );

        if (member == null) {
            throw new GroupEventNotFoundException(
                "Group membership does not exist."
            );
        }

        return member;
    }

    private GroupOrderMemberResponse lockedMemberById(
        UUID groupOrderId,
        UUID memberId
    ) {

        List<GroupOrderMemberResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    group_order_id,
                    student_id,
                    status,
                    joined_at,
                    left_at
                FROM group_order_members
                WHERE id = ?
                  AND group_order_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) ->
                    memberResponse(
                        resultSet,
                        false
                    ),
                memberId,
                groupOrderId
            );

        if (rows.size() != 1) {
            throw new GroupEventNotFoundException(
                "Group member does not exist."
            );
        }

        return rows.get(0);
    }

    private ItemContext lockedOrderItem(
        UUID orderId,
        UUID orderItemId
    ) {

        List<ItemContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    group_order_member_id
                FROM order_items
                WHERE id = ?
                  AND order_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) ->
                    new ItemContext(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "group_order_member_id",
                            UUID.class
                        )
                    ),
                orderItemId,
                orderId
            );

        if (rows.size() != 1) {
            throw new GroupEventNotFoundException(
                "Order item does not exist in group order."
            );
        }

        return rows.get(0);
    }

    private OrderContext lockedOrder(
        UUID organizationId,
        UUID orderId
    ) {

        List<OrderContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    organization_id,
                    campus_id,
                    student_id,
                    source,
                    status,
                    order_type
                FROM orders
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) ->
                    new OrderContext(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "campus_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "student_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "source"
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getString(
                            "order_type"
                        )
                    ),
                orderId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new GroupEventNotFoundException(
                "Order does not exist."
            );
        }

        return rows.get(0);
    }

    private UUID campusForGroup(
        UUID organizationId,
        UUID groupOrderId
    ) {

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT o.campus_id
                FROM group_orders g
                JOIN orders o
                  ON o.id = g.order_id
                WHERE g.id = ?
                  AND o.organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    resultSet.getObject(
                        "campus_id",
                        UUID.class
                    ),
                groupOrderId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new GroupEventNotFoundException(
                "Group order does not exist."
            );
        }

        return rows.get(0);
    }

    private StudentContext studentActor(
        UUID actorId
    ) {

        requireId(
            actorId,
            "Actor id"
        );

        List<StudentContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    s.id AS student_id,
                    s.campus_id,
                    u.organization_id
                FROM students s
                JOIN users u
                  ON u.id = s.user_id
                JOIN campuses c
                  ON c.id = s.campus_id
                WHERE s.user_id = ?
                  AND s.enrollment_status = 'ACTIVE'
                  AND c.organization_id = u.organization_id
                  AND c.is_active = TRUE
                """,
                (resultSet, rowNumber) ->
                    new StudentContext(
                        resultSet.getObject(
                            "student_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "campus_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "organization_id",
                            UUID.class
                        )
                    ),
                actorId
            );

        if (rows.size() != 1) {
            throw new BadCredentialsException(
                "Authenticated user is not an active student."
            );
        }

        return rows.get(0);
    }

    private StudentContext lockedStudentActor(
        UUID actorId
    ) {

        requireId(
            actorId,
            "Actor id"
        );

        List<StudentContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    s.id AS student_id,
                    s.campus_id,
                    u.organization_id
                FROM students s
                JOIN users u
                  ON u.id = s.user_id
                JOIN campuses c
                  ON c.id = s.campus_id
                WHERE s.user_id = ?
                  AND s.enrollment_status = 'ACTIVE'
                  AND c.organization_id = u.organization_id
                  AND c.is_active = TRUE
                FOR UPDATE OF s
                """,
                (resultSet, rowNumber) ->
                    new StudentContext(
                        resultSet.getObject(
                            "student_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "campus_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "organization_id",
                            UUID.class
                        )
                    ),
                actorId
            );

        if (rows.size() != 1) {
            throw new BadCredentialsException(
                "Authenticated user is not an active student."
            );
        }

        return rows.get(0);
    }

    private void requireSameCampus(
        StudentContext actor,
        UUID campusId
    ) {

        if (!actor.campusId().equals(campusId)) {

            throw new GroupEventNotFoundException(
                "Group order does not exist in authenticated student campus."
            );
        }
    }

    private void validateCreate(
        CreateGroupOrderCommand command
    ) {

        if (command == null) {
            throw new GroupEventValidationException(
                "Group order payload is required."
            );
        }

        normalizedJoinCode(
            command.joinCode()
        );
    }

    private String normalizedJoinCode(
        String value
    ) {

        String normalized =
            value == null
                ? null
                : value.trim();

        if (
            normalized == null
                || normalized.isEmpty()
        ) {
            throw new GroupEventValidationException(
                "Group join code is required."
            );
        }

        if (normalized.length() > 40) {
            throw new GroupEventValidationException(
                "Group join code is too long."
            );
        }

        return normalized;
    }

    private void requireId(
        UUID value,
        String label
    ) {

        if (value == null) {
            throw new GroupEventValidationException(
                label + " is required."
            );
        }
    }

    private GroupOrderResponse groupResponse(
        java.sql.ResultSet resultSet,
        boolean replayed
    ) throws java.sql.SQLException {

        return new GroupOrderResponse(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("order_id", UUID.class),
            resultSet.getObject("owner_student_id", UUID.class),
            resultSet.getString("join_code"),
            GroupOrderStatus.valueOf(
                resultSet.getString("status")
            ),
            resultSet.getObject("closes_at", OffsetDateTime.class),
            resultSet.getObject("created_at", OffsetDateTime.class),
            replayed
        );
    }

    private GroupOrderMemberResponse memberResponse(
        java.sql.ResultSet resultSet,
        boolean replayed
    ) throws java.sql.SQLException {

        return new GroupOrderMemberResponse(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("group_order_id", UUID.class),
            resultSet.getObject("student_id", UUID.class),
            GroupOrderMemberStatus.valueOf(
                resultSet.getString("status")
            ),
            resultSet.getObject("joined_at", OffsetDateTime.class),
            resultSet.getObject("left_at", OffsetDateTime.class),
            replayed
        );
    }

    private GroupContext groupContext(
        java.sql.ResultSet resultSet
    ) throws java.sql.SQLException {

        return new GroupContext(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("order_id", UUID.class),
            resultSet.getObject("owner_student_id", UUID.class),
            resultSet.getString("join_code"),
            GroupOrderStatus.valueOf(
                resultSet.getString("status")
            ),
            resultSet.getObject("closes_at", OffsetDateTime.class),
            resultSet.getObject("campus_id", UUID.class),
            resultSet.getObject("database_now", OffsetDateTime.class)
        );
    }

    private GroupOrderResponse replayGroup(
        GroupOrderResponse stored
    ) {

        return new GroupOrderResponse(
            stored.id(),
            stored.orderId(),
            stored.ownerStudentId(),
            stored.joinCode(),
            stored.status(),
            stored.closesAt(),
            stored.createdAt(),
            true
        );
    }

    private GroupOrderMemberResponse replayMember(
        GroupOrderMemberResponse stored
    ) {

        return new GroupOrderMemberResponse(
            stored.id(),
            stored.groupOrderId(),
            stored.studentId(),
            stored.status(),
            stored.joinedAt(),
            stored.leftAt(),
            true
        );
    }

    private record StudentContext(
        UUID studentId,
        UUID campusId,
        UUID organizationId
    ) {
    }

    private record OrderContext(
        UUID id,
        UUID campusId,
        UUID studentId,
        String source,
        String status,
        String orderType
    ) {
    }

    private record GroupContext(
        UUID id,
        UUID orderId,
        UUID ownerStudentId,
        String joinCode,
        GroupOrderStatus status,
        OffsetDateTime closesAt,
        UUID campusId,
        OffsetDateTime databaseNow
    ) {
    }

    private record ItemContext(
        UUID id,
        UUID groupOrderMemberId
    ) {
    }

    /**
     * PostgreSQL TIMESTAMPTZ stores fractional seconds at microsecond
     * precision. Resource-ID replay therefore compares instants using
     * the precision that survives the database round-trip instead of
     * raw OffsetDateTime nanosecond equality.
     */
    private static boolean samePostgresTimestamp(
        OffsetDateTime left,
        OffsetDateTime right
    ) {

        if (
            left == null
            || right == null
        ) {
            return left == right;
        }

        java.time.Duration delta =
            java.time.Duration
                .between(
                    left.toInstant(),
                    right.toInstant()
                )
                .abs();

        return delta.compareTo(
            java.time.Duration.ofNanos(
                1_000L
            )
        ) < 0;
    }
}