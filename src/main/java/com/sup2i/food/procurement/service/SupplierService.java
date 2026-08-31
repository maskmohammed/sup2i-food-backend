package com.sup2i.food.procurement.service;

import com.sup2i.food.procurement.api.dto.SupplierCommand;
import com.sup2i.food.procurement.api.dto.SupplierResponse;
import com.sup2i.food.procurement.exception.ProcurementConflictException;
import com.sup2i.food.procurement.exception.ProcurementNotFoundException;
import com.sup2i.food.procurement.exception.ProcurementValidationException;
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
public class SupplierService {

    private final JdbcTemplate jdbcTemplate;

    public SupplierService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public SupplierResponse create(
        UUID actorId,
        UUID supplierId,
        SupplierCommand command
    ) {

        UUID organizationId =
            organizationId(actorId);

        requireId(
            supplierId,
            "Supplier id"
        );

        validate(command);

        List<SupplierResponse> existing =
            owned(
                supplierId,
                organizationId
            );

        if (!existing.isEmpty()) {

            SupplierResponse stored =
                existing.get(0);

            if (
                stored.active()
                && samePayload(
                    stored,
                    command
                )
            ) {
                return stored;
            }

            throw new ProcurementConflictException(
                "Supplier identifier is already used by another payload."
            );
        }

        try {

            jdbcTemplate.update(
                """
                INSERT INTO suppliers(
                    id,
                    organization_id,
                    name,
                    phone,
                    email,
                    address,
                    is_active
                )
                VALUES (?, ?, ?, ?, ?, ?, TRUE)
                """,
                supplierId,
                organizationId,
                requiredText(
                    command.name(),
                    "Supplier name",
                    180
                ),
                nullableText(
                    command.phone(),
                    40,
                    "Supplier phone"
                ),
                nullableText(
                    command.email(),
                    255,
                    "Supplier email"
                ),
                nullableText(
                    command.address()
                )
            );

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new ProcurementConflictException(
                "Supplier violates a procurement invariant."
            );
        }

        return get(
            actorId,
            supplierId
        );
    }

    @Transactional
    public SupplierResponse update(
        UUID actorId,
        UUID supplierId,
        SupplierCommand command
    ) {

        UUID organizationId =
            organizationId(actorId);

        requireId(
            supplierId,
            "Supplier id"
        );

        validate(command);

        lock(
            supplierId,
            organizationId
        );

        int updated =
            jdbcTemplate.update(
                """
                UPDATE suppliers
                SET
                    name = ?,
                    phone = ?,
                    email = ?,
                    address = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND organization_id = ?
                """,
                requiredText(
                    command.name(),
                    "Supplier name",
                    180
                ),
                nullableText(
                    command.phone(),
                    40,
                    "Supplier phone"
                ),
                nullableText(
                    command.email(),
                    255,
                    "Supplier email"
                ),
                nullableText(
                    command.address()
                ),
                supplierId,
                organizationId
            );

        if (updated != 1) {
            throw new ProcurementConflictException(
                "Supplier changed concurrently."
            );
        }

        return get(
            actorId,
            supplierId
        );
    }

    @Transactional
    public SupplierResponse setActive(
        UUID actorId,
        UUID supplierId,
        boolean active
    ) {

        UUID organizationId =
            organizationId(actorId);

        SupplierResponse stored =
            lock(
                supplierId,
                organizationId
            );

        if (stored.active() == active) {
            return stored;
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE suppliers
                SET
                    is_active = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND organization_id = ?
                """,
                active,
                supplierId,
                organizationId
            );

        if (updated != 1) {
            throw new ProcurementConflictException(
                "Supplier changed concurrently."
            );
        }

        return get(
            actorId,
            supplierId
        );
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(
        UUID actorId,
        UUID supplierId
    ) {

        UUID organizationId =
            organizationId(actorId);

        requireId(
            supplierId,
            "Supplier id"
        );

        List<SupplierResponse> rows =
            owned(
                supplierId,
                organizationId
            );

        if (rows.isEmpty()) {
            throw new ProcurementNotFoundException(
                "Supplier does not exist."
            );
        }

        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> list(
        UUID actorId,
        boolean activeOnly
    ) {

        UUID organizationId =
            organizationId(actorId);

        if (activeOnly) {

            return jdbcTemplate.query(
                """
                SELECT
                    id,
                    organization_id,
                    name,
                    phone,
                    email,
                    address,
                    is_active,
                    created_at,
                    updated_at
                FROM suppliers
                WHERE organization_id = ?
                  AND is_active = TRUE
                ORDER BY name, id
                """,
                (resultSet, rowNumber) ->
                    supplierResponse(
                        resultSet
                    ),
                organizationId
            );
        }

        return jdbcTemplate.query(
            """
            SELECT
                id,
                organization_id,
                name,
                phone,
                email,
                address,
                is_active,
                created_at,
                updated_at
            FROM suppliers
            WHERE organization_id = ?
            ORDER BY name, id
            """,
            (resultSet, rowNumber) ->
                supplierResponse(
                    resultSet
                ),
            organizationId
        );
    }

    private SupplierResponse lock(
        UUID supplierId,
        UUID organizationId
    ) {

        List<SupplierResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    organization_id,
                    name,
                    phone,
                    email,
                    address,
                    is_active,
                    created_at,
                    updated_at
                FROM suppliers
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) ->
                    supplierResponse(
                        resultSet
                    ),
                supplierId,
                organizationId
            );

        if (rows.isEmpty()) {
            throw new ProcurementNotFoundException(
                "Supplier does not exist."
            );
        }

        return rows.get(0);
    }

    private List<SupplierResponse> owned(
        UUID supplierId,
        UUID organizationId
    ) {

        return jdbcTemplate.query(
            """
            SELECT
                id,
                organization_id,
                name,
                phone,
                email,
                address,
                is_active,
                created_at,
                updated_at
            FROM suppliers
            WHERE id = ?
              AND organization_id = ?
            """,
            (resultSet, rowNumber) ->
                supplierResponse(
                    resultSet
                ),
            supplierId,
            organizationId
        );
    }

    private SupplierResponse supplierResponse(
        java.sql.ResultSet resultSet
    ) throws java.sql.SQLException {

        return new SupplierResponse(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "organization_id",
                UUID.class
            ),
            resultSet.getString(
                "name"
            ),
            resultSet.getString(
                "phone"
            ),
            resultSet.getString(
                "email"
            ),
            resultSet.getString(
                "address"
            ),
            resultSet.getBoolean(
                "is_active"
            ),
            resultSet.getObject(
                "created_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "updated_at",
                OffsetDateTime.class
            )
        );
    }

    private boolean samePayload(
        SupplierResponse stored,
        SupplierCommand command
    ) {

        return Objects.equals(
            stored.name(),
            requiredText(
                command.name(),
                "Supplier name",
                180
            )
        )
            && Objects.equals(
                stored.phone(),
                nullableText(
                    command.phone(),
                    40,
                    "Supplier phone"
                )
            )
            && Objects.equals(
                stored.email(),
                nullableText(
                    command.email(),
                    255,
                    "Supplier email"
                )
            )
            && Objects.equals(
                stored.address(),
                nullableText(
                    command.address()
                )
            );
    }

    private void validate(
        SupplierCommand command
    ) {

        if (command == null) {
            throw new ProcurementValidationException(
                "Supplier payload is required."
            );
        }

        requiredText(
            command.name(),
            "Supplier name",
            180
        );

        nullableText(
            command.phone(),
            40,
            "Supplier phone"
        );

        nullableText(
            command.email(),
            255,
            "Supplier email"
        );
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
            throw new ProcurementValidationException(
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
            nullableText(
                value
            );

        if (normalized == null) {
            throw new ProcurementValidationException(
                label + " is required."
            );
        }

        if (normalized.length() > maxLength) {
            throw new ProcurementValidationException(
                label + " is too long."
            );
        }

        return normalized;
    }

    private String nullableText(
        String value,
        int maxLength,
        String label
    ) {

        String normalized =
            nullableText(
                value
            );

        if (
            normalized != null
            && normalized.length() > maxLength
        ) {
            throw new ProcurementValidationException(
                label + " is too long."
            );
        }

        return normalized;
    }

    private String nullableText(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }
}