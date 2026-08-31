package com.sup2i.food.waste.service;

import com.sup2i.food.waste.api.dto.WasteReasonCommand;
import com.sup2i.food.waste.api.dto.WasteReasonResponse;
import com.sup2i.food.waste.domain.WasteCategory;
import com.sup2i.food.waste.exception.WasteConflictException;
import com.sup2i.food.waste.exception.WasteNotFoundException;
import com.sup2i.food.waste.exception.WasteValidationException;
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
public class WasteReasonService {

    private final JdbcTemplate jdbcTemplate;

    public WasteReasonService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public WasteReasonResponse create(
        UUID actorId,
        UUID reasonId,
        WasteReasonCommand command
    ) {

        UUID organizationId =
            organizationId(actorId);

        requireId(
            reasonId,
            "Waste reason id"
        );

        validate(command);

        WasteReasonResponse existing =
            find(
                organizationId,
                reasonId,
                false
            );

        if (existing != null) {

            if (
                samePayload(
                    existing,
                    command
                )
            ) {
                return existing;
            }

            throw new WasteConflictException(
                "Waste reason identifier is already used by another payload."
            );
        }

        try {

            jdbcTemplate.update(
                """
                INSERT INTO waste_reasons(
                    id,
                    organization_id,
                    code,
                    name,
                    category,
                    requires_comment,
                    is_active
                )
                VALUES (?, ?, ?, ?, ?, ?, TRUE)
                """,
                reasonId,
                organizationId,
                requiredText(
                    command.code(),
                    "Waste reason code",
                    80
                ),
                requiredText(
                    command.name(),
                    "Waste reason name",
                    150
                ),
                command.category()
                    .name(),
                command.requiresComment()
            );

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new WasteConflictException(
                "Waste reason violates a uniqueness or schema invariant."
            );
        }

        return get(
            actorId,
            reasonId
        );
    }

    @Transactional
    public WasteReasonResponse update(
        UUID actorId,
        UUID reasonId,
        WasteReasonCommand command
    ) {

        UUID organizationId =
            organizationId(actorId);

        requireId(
            reasonId,
            "Waste reason id"
        );

        validate(command);

        locked(
            organizationId,
            reasonId
        );

        try {

            int updated =
                jdbcTemplate.update(
                    """
                    UPDATE waste_reasons
                    SET
                        code = ?,
                        name = ?,
                        category = ?,
                        requires_comment = ?
                    WHERE id = ?
                      AND organization_id = ?
                    """,
                    requiredText(
                        command.code(),
                        "Waste reason code",
                        80
                    ),
                    requiredText(
                        command.name(),
                        "Waste reason name",
                        150
                    ),
                    command.category()
                        .name(),
                    command.requiresComment(),
                    reasonId,
                    organizationId
                );

            if (updated != 1) {
                throw new WasteConflictException(
                    "Waste reason changed concurrently."
                );
            }

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new WasteConflictException(
                "Waste reason violates a uniqueness or schema invariant."
            );
        }

        return get(
            actorId,
            reasonId
        );
    }

    @Transactional
    public WasteReasonResponse setActive(
        UUID actorId,
        UUID reasonId,
        boolean active
    ) {

        UUID organizationId =
            organizationId(actorId);

        WasteReasonResponse stored =
            locked(
                organizationId,
                reasonId
            );

        if (stored.active() == active) {
            return stored;
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE waste_reasons
                SET is_active = ?
                WHERE id = ?
                  AND organization_id = ?
                """,
                active,
                reasonId,
                organizationId
            );

        if (updated != 1) {
            throw new WasteConflictException(
                "Waste reason changed concurrently."
            );
        }

        return get(
            actorId,
            reasonId
        );
    }

    @Transactional(readOnly = true)
    public WasteReasonResponse get(
        UUID actorId,
        UUID reasonId
    ) {

        UUID organizationId =
            organizationId(actorId);

        requireId(
            reasonId,
            "Waste reason id"
        );

        WasteReasonResponse reason =
            find(
                organizationId,
                reasonId,
                false
            );

        if (reason == null) {
            throw new WasteNotFoundException(
                "Waste reason does not exist."
            );
        }

        return reason;
    }

    @Transactional(readOnly = true)
    public List<WasteReasonResponse> list(
        UUID actorId,
        boolean activeOnly
    ) {

        UUID organizationId =
            organizationId(actorId);

        String sql =
            activeOnly
                ? """
                  SELECT
                      id,
                      organization_id,
                      code,
                      name,
                      category,
                      requires_comment,
                      is_active,
                      created_at
                  FROM waste_reasons
                  WHERE organization_id = ?
                    AND is_active = TRUE
                  ORDER BY name, id
                  """
                : """
                  SELECT
                      id,
                      organization_id,
                      code,
                      name,
                      category,
                      requires_comment,
                      is_active,
                      created_at
                  FROM waste_reasons
                  WHERE organization_id = ?
                  ORDER BY name, id
                  """;

        return jdbcTemplate.query(
            sql,
            (resultSet, rowNumber) ->
                response(resultSet),
            organizationId
        );
    }

    private WasteReasonResponse locked(
        UUID organizationId,
        UUID reasonId
    ) {

        WasteReasonResponse reason =
            find(
                organizationId,
                reasonId,
                true
            );

        if (reason == null) {
            throw new WasteNotFoundException(
                "Waste reason does not exist."
            );
        }

        return reason;
    }

    private WasteReasonResponse find(
        UUID organizationId,
        UUID reasonId,
        boolean lock
    ) {

        String sql =
            """
            SELECT
                id,
                organization_id,
                code,
                name,
                category,
                requires_comment,
                is_active,
                created_at
            FROM waste_reasons
            WHERE id = ?
              AND organization_id = ?
            """
                + (
                    lock
                        ? " FOR UPDATE"
                        : ""
                );

        List<WasteReasonResponse> rows =
            jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                    response(resultSet),
                reasonId,
                organizationId
            );

        if (rows.size() > 1) {
            throw new WasteConflictException(
                "Multiple waste reasons matched one identifier."
            );
        }

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private WasteReasonResponse response(
        java.sql.ResultSet resultSet
    ) throws java.sql.SQLException {

        return new WasteReasonResponse(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "organization_id",
                UUID.class
            ),
            resultSet.getString(
                "code"
            ),
            resultSet.getString(
                "name"
            ),
            WasteCategory.valueOf(
                resultSet.getString(
                    "category"
                )
            ),
            resultSet.getBoolean(
                "requires_comment"
            ),
            resultSet.getBoolean(
                "is_active"
            ),
            resultSet.getObject(
                "created_at",
                OffsetDateTime.class
            )
        );
    }

    private boolean samePayload(
        WasteReasonResponse stored,
        WasteReasonCommand requested
    ) {

        return Objects.equals(
            stored.code(),
            requiredText(
                requested.code(),
                "Waste reason code",
                80
            )
        )
            && Objects.equals(
                stored.name(),
                requiredText(
                    requested.name(),
                    "Waste reason name",
                    150
                )
            )
            && stored.category()
                == requested.category()
            && stored.requiresComment()
                == requested.requiresComment();
    }

    private void validate(
        WasteReasonCommand command
    ) {

        if (command == null) {
            throw new WasteValidationException(
                "Waste reason payload is required."
            );
        }

        requiredText(
            command.code(),
            "Waste reason code",
            80
        );

        requiredText(
            command.name(),
            "Waste reason name",
            150
        );

        if (command.category() == null) {
            throw new WasteValidationException(
                "Waste reason category is required."
            );
        }
    }

    private UUID organizationId(
        UUID actorId
    ) {

        requireId(
            actorId,
            "Actor id"
        );

        List<UUID> organizations =
            jdbcTemplate.query(
                """
                SELECT organization_id
                FROM users
                WHERE id = ?
                """,
                (resultSet, rowNumber) ->
                    resultSet.getObject(
                        "organization_id",
                        UUID.class
                    ),
                actorId
            );

        if (organizations.size() != 1) {
            throw new BadCredentialsException(
                "Authenticated user does not exist."
            );
        }

        return organizations.get(0);
    }

    private void requireId(
        UUID value,
        String label
    ) {

        if (value == null) {
            throw new WasteValidationException(
                label + " is required."
            );
        }
    }

    private String requiredText(
        String value,
        String label,
        int maxLength
    ) {

        String normalized =
            value == null
                ? null
                : value.trim();

        if (
            normalized == null
            || normalized.isEmpty()
        ) {
            throw new WasteValidationException(
                label + " is required."
            );
        }

        if (normalized.length() > maxLength) {
            throw new WasteValidationException(
                label + " is too long."
            );
        }

        return normalized;
    }
}