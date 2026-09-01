package com.sup2i.food.interaction.service;

import com.sup2i.food.interaction.api.dto.CreateProductInteractionCommand;
import com.sup2i.food.interaction.api.dto.ProductInteractionResponse;
import com.sup2i.food.interaction.domain.ProductInteractionType;
import com.sup2i.food.interaction.exception.ProductInteractionConflictException;
import com.sup2i.food.interaction.exception.ProductInteractionNotFoundException;
import com.sup2i.food.interaction.exception.ProductInteractionValidationException;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProductInteractionService {

    private static final int MAX_LIST_LIMIT =
        200;

    private final JdbcTemplate jdbcTemplate;

    public ProductInteractionService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public ProductInteractionResponse record(
        UUID organizationId,
        UUID interactionId,
        CreateProductInteractionCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            interactionId,
            "Interaction id"
        );

        validateCommand(
            command
        );

        String metadataJson =
            canonicalNullableJson(
                command.metadataJson(),
                "Interaction metadata"
            );

        lockProduct(
            organizationId,
            command.productId()
        );

        validateOptionalStudent(
            organizationId,
            command.studentId()
        );

        validateOptionalCart(
            organizationId,
            command.cartId()
        );

        validateOptionalOrder(
            organizationId,
            command.orderId()
        );

        validateOptionalLocation(
            organizationId,
            command.locationId()
        );

        ProductInteractionResponse existing =
            findById(
                organizationId,
                interactionId
            );

        if (existing != null) {

            if (
                samePayload(
                    existing,
                    command,
                    metadataJson
                )
            ) {
                return replay(
                    existing
                );
            }

            throw new ProductInteractionConflictException(
                "Interaction identifier is already used by another payload."
            );
        }

        int inserted;

        try {

            inserted =
                jdbcTemplate.update(
                    """
                    INSERT INTO product_interaction_events(
                        id,
                        student_id,
                        product_id,
                        event_type,
                        cart_id,
                        order_id,
                        location_id,
                        occurred_at,
                        metadata
                    )
                    VALUES(
                        ?, ?, ?, ?, ?, ?, ?,
                        COALESCE(
                            CAST(? AS TIMESTAMPTZ),
                            CURRENT_TIMESTAMP
                        ),
                        CAST(? AS JSONB)
                    )
                    ON CONFLICT (id)
                    DO NOTHING
                    """,
                    interactionId,
                    command.studentId(),
                    command.productId(),
                    command.eventType().name(),
                    command.cartId(),
                    command.orderId(),
                    command.locationId(),
                    command.occurredAt(),
                    metadataJson
                );

        } catch (DataAccessException exception) {

            throw new ProductInteractionConflictException(
                "Interaction conflicts with an existing database resource."
            );
        }

        ProductInteractionResponse stored =
            findById(
                organizationId,
                interactionId
            );

        if (stored == null) {

            throw new ProductInteractionConflictException(
                "Interaction identifier conflicts with another tenant resource."
            );
        }

        if (inserted == 0) {

            if (
                samePayload(
                    stored,
                    command,
                    metadataJson
                )
            ) {
                return replay(
                    stored
                );
            }

            throw new ProductInteractionConflictException(
                "Interaction identifier is already used by another payload."
            );
        }

        return stored;
    }

    @Transactional(readOnly = true)
    public ProductInteractionResponse get(
        UUID organizationId,
        UUID interactionId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            interactionId,
            "Interaction id"
        );

        ProductInteractionResponse response =
            findById(
                organizationId,
                interactionId
            );

        if (response == null) {

            throw new ProductInteractionNotFoundException(
                "Product interaction does not exist."
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<ProductInteractionResponse> listForProduct(
        UUID organizationId,
        UUID productId,
        int limit
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            productId,
            "Product id"
        );

        int safeLimit =
            safeLimit(
                limit
            );

        requireProduct(
            organizationId,
            productId
        );

        return jdbcTemplate.query(
            """
            SELECT
                e.id,
                e.student_id,
                e.product_id,
                e.event_type,
                e.cart_id,
                e.order_id,
                e.location_id,
                e.occurred_at,
                e.metadata::text AS metadata_json
            FROM product_interaction_events e
            JOIN products p
              ON p.id = e.product_id
            WHERE p.organization_id = ?
              AND e.product_id = ?
            ORDER BY e.occurred_at DESC, e.id
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
                    ProductInteractionType.valueOf(
                        rs.getString(
                            "event_type"
                        )
                    ),
                    rs.getObject(
                        "cart_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "order_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "location_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "occurred_at",
                        OffsetDateTime.class
                    ),
                    rs.getString(
                        "metadata_json"
                    ),
                    false
                ),
            organizationId,
            productId,
            safeLimit
        );
    }

    @Transactional(readOnly = true)
    public List<ProductInteractionResponse> listForStudent(
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

        requireStudent(
            organizationId,
            studentId
        );

        return jdbcTemplate.query(
            """
            SELECT
                e.id,
                e.student_id,
                e.product_id,
                e.event_type,
                e.cart_id,
                e.order_id,
                e.location_id,
                e.occurred_at,
                e.metadata::text AS metadata_json
            FROM product_interaction_events e
            JOIN students s
              ON s.id = e.student_id
            JOIN users u
              ON u.id = s.user_id
            JOIN products p
              ON p.id = e.product_id
            WHERE u.organization_id = ?
              AND p.organization_id = ?
              AND e.student_id = ?
            ORDER BY e.occurred_at DESC, e.id
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
                    ProductInteractionType.valueOf(
                        rs.getString(
                            "event_type"
                        )
                    ),
                    rs.getObject(
                        "cart_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "order_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "location_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "occurred_at",
                        OffsetDateTime.class
                    ),
                    rs.getString(
                        "metadata_json"
                    ),
                    false
                ),
            organizationId,
            organizationId,
            studentId,
            safeLimit
        );
    }

    private ProductInteractionResponse findById(
        UUID organizationId,
        UUID interactionId
    ) {

        List<ProductInteractionResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    e.id,
                    e.student_id,
                    e.product_id,
                    e.event_type,
                    e.cart_id,
                    e.order_id,
                    e.location_id,
                    e.occurred_at,
                    e.metadata::text AS metadata_json
                FROM product_interaction_events e
                JOIN products p
                  ON p.id = e.product_id
                WHERE e.id = ?
                  AND p.organization_id = ?
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
                        ProductInteractionType.valueOf(
                            rs.getString(
                                "event_type"
                            )
                        ),
                        rs.getObject(
                            "cart_id",
                            UUID.class
                        ),
                        rs.getObject(
                            "order_id",
                            UUID.class
                        ),
                        rs.getObject(
                            "location_id",
                            UUID.class
                        ),
                        rs.getObject(
                            "occurred_at",
                            OffsetDateTime.class
                        ),
                        rs.getString(
                            "metadata_json"
                        ),
                        false
                    ),
                interactionId,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(
            0
        );
    }

    private ProductInteractionResponse map(
        UUID id,
        UUID studentId,
        UUID productId,
        ProductInteractionType eventType,
        UUID cartId,
        UUID orderId,
        UUID locationId,
        OffsetDateTime occurredAt,
        String metadataJson,
        boolean replayed
    ) {

        return new ProductInteractionResponse(
            id,
            studentId,
            productId,
            eventType,
            cartId,
            orderId,
            locationId,
            occurredAt,
            metadataJson,
            replayed
        );
    }

    private ProductInteractionResponse replay(
        ProductInteractionResponse response
    ) {

        return map(
            response.id(),
            response.studentId(),
            response.productId(),
            response.eventType(),
            response.cartId(),
            response.orderId(),
            response.locationId(),
            response.occurredAt(),
            response.metadataJson(),
            true
        );
    }

    private boolean samePayload(
        ProductInteractionResponse existing,
        CreateProductInteractionCommand command,
        String metadataJson
    ) {

        boolean sameTimestamp =
            command.occurredAt() == null
                || samePostgresTimestamp(
                    existing.occurredAt(),
                    command.occurredAt()
                );

        return Objects.equals(
            existing.studentId(),
            command.studentId()
        )
            && existing.productId().equals(
                command.productId()
            )
            && existing.eventType()
                == command.eventType()
            && Objects.equals(
                existing.cartId(),
                command.cartId()
            )
            && Objects.equals(
                existing.orderId(),
                command.orderId()
            )
            && Objects.equals(
                existing.locationId(),
                command.locationId()
            )
            && sameTimestamp
            && Objects.equals(
                existing.metadataJson(),
                metadataJson
            );
    }

    private boolean samePostgresTimestamp(
        OffsetDateTime left,
        OffsetDateTime right
    ) {

        if (
            left == null
            || right == null
        ) {
            return left == right;
        }

        Duration difference =
            Duration.between(
                left.toInstant(),
                right.toInstant()
            )
                .abs();

        return difference.compareTo(
            Duration.ofNanos(
                1_000L
            )
        ) < 0;
    }

    private void validateCommand(
        CreateProductInteractionCommand command
    ) {

        if (command == null) {

            throw new ProductInteractionValidationException(
                "Interaction command is required."
            );
        }

        requireId(
            command.productId(),
            "Product id"
        );

        if (command.eventType() == null) {

            throw new ProductInteractionValidationException(
                "Interaction event type is required."
            );
        }
    }

    private void lockProduct(
        UUID organizationId,
        UUID productId
    ) {

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT id
                FROM products
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                productId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new ProductInteractionNotFoundException(
                "Product does not exist."
            );
        }
    }

    private void requireProduct(
        UUID organizationId,
        UUID productId
    ) {

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT id
                FROM products
                WHERE id = ?
                  AND organization_id = ?
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                productId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new ProductInteractionNotFoundException(
                "Product does not exist."
            );
        }
    }

    private void validateOptionalStudent(
        UUID organizationId,
        UUID studentId
    ) {

        if (studentId == null) {
            return;
        }

        requireStudent(
            organizationId,
            studentId
        );
    }

    private void requireStudent(
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

            throw new ProductInteractionNotFoundException(
                "Student does not exist."
            );
        }
    }

    private void validateOptionalCart(
        UUID organizationId,
        UUID cartId
    ) {

        if (cartId == null) {
            return;
        }

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT sc.id
                FROM shopping_carts sc
                JOIN students s
                  ON s.id = sc.student_id
                JOIN users u
                  ON u.id = s.user_id
                JOIN locations l
                  ON l.id = sc.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE sc.id = ?
                  AND u.organization_id = ?
                  AND c.organization_id = ?
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                cartId,
                organizationId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new ProductInteractionNotFoundException(
                "Shopping cart does not exist."
            );
        }
    }

    private void validateOptionalOrder(
        UUID organizationId,
        UUID orderId
    ) {

        if (orderId == null) {
            return;
        }

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT id
                FROM orders
                WHERE id = ?
                  AND organization_id = ?
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                orderId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new ProductInteractionNotFoundException(
                "Order does not exist."
            );
        }
    }

    private void validateOptionalLocation(
        UUID organizationId,
        UUID locationId
    ) {

        if (locationId == null) {
            return;
        }

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT l.id
                FROM locations l
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE l.id = ?
                  AND c.organization_id = ?
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                locationId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new ProductInteractionNotFoundException(
                "Location does not exist."
            );
        }
    }

    private String canonicalNullableJson(
        String value,
        String label
    ) {

        if (value == null) {
            return null;
        }

        if (value.trim().isEmpty()) {

            throw new ProductInteractionValidationException(
                label + " must contain valid JSON."
            );
        }

        try {

            String canonical =
                jdbcTemplate.queryForObject(
                    """
                    SELECT CAST(? AS JSONB)::text
                    """,
                    String.class,
                    value
                );

            if (canonical == null) {

                throw new ProductInteractionValidationException(
                    label + " must contain valid JSON."
                );
            }

            return canonical;

        } catch (DataAccessException exception) {

            throw new ProductInteractionValidationException(
                label + " must contain valid JSON."
            );
        }
    }

    private int safeLimit(
        int limit
    ) {

        if (
            limit < 1
            || limit > MAX_LIST_LIMIT
        ) {

            throw new ProductInteractionValidationException(
                "List limit must be between 1 and 200."
            );
        }

        return limit;
    }

    private void requireId(
        UUID id,
        String label
    ) {

        if (id == null) {

            throw new ProductInteractionValidationException(
                label + " is required."
            );
        }
    }
}