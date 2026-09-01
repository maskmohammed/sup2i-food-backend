package com.sup2i.food.review.service;

import com.sup2i.food.review.api.dto.CreateReviewCommand;
import com.sup2i.food.review.api.dto.ReviewResponse;
import com.sup2i.food.review.domain.ReviewTargetType;
import com.sup2i.food.review.exception.ReviewConflictException;
import com.sup2i.food.review.exception.ReviewNotFoundException;
import com.sup2i.food.review.exception.ReviewValidationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReviewService {

    private static final int MAX_LIST_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    public ReviewService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public ReviewResponse create(
        UUID organizationId,
        UUID reviewId,
        UUID studentId,
        CreateReviewCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            reviewId,
            "Review id"
        );

        requireId(
            studentId,
            "Student id"
        );

        validateCommand(
            command
        );

        String comment =
            nullableComment(
                command.comment()
            );

        lockStudent(
            organizationId,
            studentId
        );

        validateTargetTenant(
            organizationId,
            command.targetType(),
            command.targetId()
        );

        ReviewResponse existing =
            findById(
                organizationId,
                reviewId
            );

        if (existing != null) {

            boolean same =
                existing.studentId().equals(
                    studentId
                )
                    && existing.targetType()
                        == command.targetType()
                    && existing.targetId().equals(
                        command.targetId()
                    )
                    && existing.rating()
                        == command.rating()
                    && Objects.equals(
                        existing.comment(),
                        comment
                    );

            if (same) {
                return replay(
                    existing
                );
            }

            throw new ReviewConflictException(
                "Review identifier is already used by another payload."
            );
        }

        UUID productId =
            null;

        UUID orderId =
            null;

        UUID menuId =
            null;

        if (
            command.targetType()
                == ReviewTargetType.PRODUCT
        ) {
            productId =
                command.targetId();
        }

        if (
            command.targetType()
                == ReviewTargetType.ORDER
        ) {
            orderId =
                command.targetId();
        }

        if (
            command.targetType()
                == ReviewTargetType.MENU
        ) {
            menuId =
                command.targetId();
        }

        try {

            jdbcTemplate.update(
                """
                INSERT INTO reviews(
                    id,
                    student_id,
                    product_id,
                    order_id,
                    menu_id,
                    rating,
                    comment
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                reviewId,
                studentId,
                productId,
                orderId,
                menuId,
                command.rating(),
                comment
            );

        } catch (DataIntegrityViolationException exception) {

            throw new ReviewConflictException(
                "Review conflicts with an existing database resource."
            );
        }

        return get(
            organizationId,
            reviewId
        );
    }

    @Transactional(readOnly = true)
    public ReviewResponse get(
        UUID organizationId,
        UUID reviewId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            reviewId,
            "Review id"
        );

        ReviewResponse response =
            findById(
                organizationId,
                reviewId
            );

        if (response == null) {

            throw new ReviewNotFoundException(
                "Review does not exist."
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listForStudent(
        UUID organizationId,
        UUID studentId,
        int limit
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            studentId,
            "Student id"
        );

        int safeLimit =
            safeLimit(
                limit
            );

        return jdbcTemplate.query(
            """
            SELECT
                r.id,
                r.student_id,
                r.product_id,
                r.order_id,
                r.menu_id,
                r.rating,
                r.comment,
                r.created_at
            FROM reviews r
            JOIN students s
              ON s.id = r.student_id
            JOIN users u
              ON u.id = s.user_id
            WHERE u.organization_id = ?
              AND r.student_id = ?
            ORDER BY r.created_at DESC, r.id
            LIMIT ?
            """,
            (rs, rowNum) ->
                map(
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                    rs.getObject(
                        "student_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "product_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "order_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "menu_id",
                        UUID.class
                    ),
                    rs.getInt(
                        "rating"
                    ),
                    rs.getString(
                        "comment"
                    ),
                    rs.getObject(
                        "created_at",
                        OffsetDateTime.class
                    ),
                    false
                ),
            organizationId,
            studentId,
            safeLimit
        );
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listForTarget(
        UUID organizationId,
        ReviewTargetType targetType,
        UUID targetId,
        int limit
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        if (targetType == null) {

            throw new ReviewValidationException(
                "Review target type is required."
            );
        }

        requireId(
            targetId,
            "Review target id"
        );

        int safeLimit =
            safeLimit(
                limit
            );

        String column =
            switch (targetType) {
                case PRODUCT -> "product_id";
                case ORDER -> "order_id";
                case MENU -> "menu_id";
            };

        String sql =
            """
            SELECT
                r.id,
                r.student_id,
                r.product_id,
                r.order_id,
                r.menu_id,
                r.rating,
                r.comment,
                r.created_at
            FROM reviews r
            JOIN students s
              ON s.id = r.student_id
            JOIN users u
              ON u.id = s.user_id
            WHERE u.organization_id = ?
              AND r.
            """
                + column
                + """
                 = ?
                ORDER BY r.created_at DESC, r.id
                LIMIT ?
                """;

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) ->
                map(
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                    rs.getObject(
                        "student_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "product_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "order_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "menu_id",
                        UUID.class
                    ),
                    rs.getInt(
                        "rating"
                    ),
                    rs.getString(
                        "comment"
                    ),
                    rs.getObject(
                        "created_at",
                        OffsetDateTime.class
                    ),
                    false
                ),
            organizationId,
            targetId,
            safeLimit
        );
    }

    private ReviewResponse findById(
        UUID organizationId,
        UUID reviewId
    ) {

        List<ReviewResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    r.id,
                    r.student_id,
                    r.product_id,
                    r.order_id,
                    r.menu_id,
                    r.rating,
                    r.comment,
                    r.created_at
                FROM reviews r
                JOIN students s
                  ON s.id = r.student_id
                JOIN users u
                  ON u.id = s.user_id
                WHERE r.id = ?
                  AND u.organization_id = ?
                """,
                (rs, rowNum) ->
                    map(
                        rs.getObject(
                            "id",
                            UUID.class
                        ),
                        rs.getObject(
                            "student_id",
                            UUID.class
                        ),
                        rs.getObject(
                            "product_id",
                            UUID.class
                        ),
                        rs.getObject(
                            "order_id",
                            UUID.class
                        ),
                        rs.getObject(
                            "menu_id",
                            UUID.class
                        ),
                        rs.getInt(
                            "rating"
                        ),
                        rs.getString(
                            "comment"
                        ),
                        rs.getObject(
                            "created_at",
                            OffsetDateTime.class
                        ),
                        false
                    ),
                reviewId,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.getFirst();
    }

    private ReviewResponse map(
        UUID id,
        UUID studentId,
        UUID productId,
        UUID orderId,
        UUID menuId,
        int rating,
        String comment,
        OffsetDateTime createdAt,
        boolean replayed
    ) {

        ReviewTargetType targetType;
        UUID targetId;

        if (productId != null) {

            targetType =
                ReviewTargetType.PRODUCT;

            targetId =
                productId;

        } else if (orderId != null) {

            targetType =
                ReviewTargetType.ORDER;

            targetId =
                orderId;

        } else if (menuId != null) {

            targetType =
                ReviewTargetType.MENU;

            targetId =
                menuId;

        } else {

            throw new ReviewConflictException(
                "Stored review has no target."
            );
        }

        return new ReviewResponse(
            id,
            studentId,
            targetType,
            targetId,
            rating,
            comment,
            createdAt,
            replayed
        );
    }

    private ReviewResponse replay(
        ReviewResponse response
    ) {

        return new ReviewResponse(
            response.id(),
            response.studentId(),
            response.targetType(),
            response.targetId(),
            response.rating(),
            response.comment(),
            response.createdAt(),
            true
        );
    }

    private void validateCommand(
        CreateReviewCommand command
    ) {

        if (command == null) {

            throw new ReviewValidationException(
                "Review command is required."
            );
        }

        if (command.targetType() == null) {

            throw new ReviewValidationException(
                "Review target type is required."
            );
        }

        requireId(
            command.targetId(),
            "Review target id"
        );

        if (
            command.rating() < 1
            || command.rating() > 5
        ) {

            throw new ReviewValidationException(
                "Review rating must be between 1 and 5."
            );
        }
    }

    private void lockStudent(
        UUID organizationId,
        UUID studentId
    ) {

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT s.id
                FROM students s
                JOIN users u
                  ON u.id = s.user_id
                WHERE s.id = ?
                  AND u.organization_id = ?
                FOR UPDATE OF s
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                studentId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new ReviewNotFoundException(
                "Student does not exist in this organization."
            );
        }
    }

    private void validateTargetTenant(
        UUID organizationId,
        ReviewTargetType targetType,
        UUID targetId
    ) {

        boolean exists =
            switch (targetType) {

                case PRODUCT ->
                    exists(
                        """
                        SELECT EXISTS(
                            SELECT 1
                            FROM products
                            WHERE id = ?
                              AND organization_id = ?
                        )
                        """,
                        targetId,
                        organizationId
                    );

                case ORDER ->
                    exists(
                        """
                        SELECT EXISTS(
                            SELECT 1
                            FROM orders
                            WHERE id = ?
                              AND organization_id = ?
                        )
                        """,
                        targetId,
                        organizationId
                    );

                case MENU ->
                    exists(
                        """
                        SELECT EXISTS(
                            SELECT 1
                            FROM canteen_menus cm
                            JOIN locations l
                              ON l.id = cm.location_id
                            JOIN campuses c
                              ON c.id = l.campus_id
                            WHERE cm.id = ?
                              AND c.organization_id = ?
                        )
                        """,
                        targetId,
                        organizationId
                    );
            };

        if (!exists) {

            throw new ReviewNotFoundException(
                "Review target does not exist in this organization."
            );
        }
    }

    private boolean exists(
        String sql,
        UUID resourceId,
        UUID organizationId
    ) {

        Boolean value =
            jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                resourceId,
                organizationId
            );

        return Boolean.TRUE.equals(
            value
        );
    }

    private String nullableComment(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed;
    }

    private int safeLimit(
        int value
    ) {

        if (value < 1) {
            return 50;
        }

        return Math.min(
            value,
            MAX_LIST_LIMIT
        );
    }

    private void requireId(
        UUID value,
        String label
    ) {

        if (value == null) {

            throw new ReviewValidationException(
                label + " is required."
            );
        }
    }
}