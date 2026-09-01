package com.sup2i.food.forecast.service;

import com.sup2i.food.forecast.api.dto.CreateDemandForecastCommand;
import com.sup2i.food.forecast.api.dto.DemandForecastResponse;
import com.sup2i.food.forecast.domain.ForecastSubjectType;
import com.sup2i.food.forecast.exception.DemandForecastConflictException;
import com.sup2i.food.forecast.exception.DemandForecastNotFoundException;
import com.sup2i.food.forecast.exception.DemandForecastValidationException;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DemandForecastService {

    private static final int MAX_LIST_LIMIT =
        200;

    private static final BigDecimal ZERO =
        BigDecimal.ZERO;

    private static final BigDecimal ONE =
        BigDecimal.ONE;

    private final JdbcTemplate jdbcTemplate;

    public DemandForecastService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public DemandForecastResponse ingest(
        UUID organizationId,
        UUID forecastId,
        CreateDemandForecastCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            forecastId,
            "Forecast id"
        );

        validateCommand(
            command
        );

        String modelName =
            nullableTrimmed(
                command.modelName(),
                120,
                "Model name"
            );

        String modelVersion =
            nullableTrimmed(
                command.modelVersion(),
                80,
                "Model version"
            );

        String featuresSnapshotJson =
            canonicalNullableJson(
                command.featuresSnapshotJson(),
                "Features snapshot"
            );

        lockLocation(
            organizationId,
            command.locationId()
        );

        lockSubject(
            organizationId,
            command.subjectType(),
            command.subjectId()
        );

        validateOptionalTimeSlot(
            organizationId,
            command.timeSlotId()
        );

        DemandForecastResponse existing =
            findById(
                organizationId,
                forecastId
            );

        if (existing != null) {

            if (
                samePayload(
                    existing,
                    command,
                    modelName,
                    modelVersion,
                    featuresSnapshotJson
                )
            ) {
                return replay(
                    existing
                );
            }

            throw new DemandForecastConflictException(
                "Forecast identifier is already used by another payload."
            );
        }

        UUID productId =
            null;

        UUID ingredientId =
            null;

        if (
            command.subjectType()
                == ForecastSubjectType.PRODUCT
        ) {
            productId =
                command.subjectId();
        }

        if (
            command.subjectType()
                == ForecastSubjectType.INGREDIENT
        ) {
            ingredientId =
                command.subjectId();
        }

        int inserted;

        try {

            inserted =
                jdbcTemplate.update(
                    """
                    INSERT INTO demand_forecasts(
                        id,
                        location_id,
                        product_id,
                        ingredient_id,
                        forecast_date,
                        time_slot_id,
                        predicted_quantity,
                        confidence_score,
                        model_name,
                        model_version,
                        features_snapshot
                    )
                    VALUES(
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        CAST(? AS JSONB)
                    )
                    ON CONFLICT (id)
                    DO NOTHING
                    """,
                    forecastId,
                    command.locationId(),
                    productId,
                    ingredientId,
                    command.forecastDate(),
                    command.timeSlotId(),
                    command.predictedQuantity(),
                    command.confidenceScore(),
                    modelName,
                    modelVersion,
                    featuresSnapshotJson
                );

        } catch (DataAccessException exception) {

            throw new DemandForecastConflictException(
                "Forecast conflicts with an existing database resource."
            );
        }

        DemandForecastResponse stored =
            findById(
                organizationId,
                forecastId
            );

        if (stored == null) {

            throw new DemandForecastConflictException(
                "Forecast identifier conflicts with another tenant resource."
            );
        }

        if (inserted == 0) {

            if (
                samePayload(
                    stored,
                    command,
                    modelName,
                    modelVersion,
                    featuresSnapshotJson
                )
            ) {
                return replay(
                    stored
                );
            }

            throw new DemandForecastConflictException(
                "Forecast identifier is already used by another payload."
            );
        }

        return stored;
    }

    @Transactional(readOnly = true)
    public DemandForecastResponse get(
        UUID organizationId,
        UUID forecastId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            forecastId,
            "Forecast id"
        );

        DemandForecastResponse response =
            findById(
                organizationId,
                forecastId
            );

        if (response == null) {

            throw new DemandForecastNotFoundException(
                "Demand forecast does not exist."
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<DemandForecastResponse> listForLocationAndDate(
        UUID organizationId,
        UUID locationId,
        LocalDate forecastDate,
        int limit
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            locationId,
            "Location id"
        );

        if (forecastDate == null) {

            throw new DemandForecastValidationException(
                "Forecast date is required."
            );
        }

        int safeLimit =
            safeLimit(
                limit
            );

        requireLocation(
            organizationId,
            locationId
        );

        return jdbcTemplate.query(
            """
            SELECT
                f.id,
                f.location_id,
                f.product_id,
                f.ingredient_id,
                f.forecast_date,
                f.time_slot_id,
                f.predicted_quantity,
                f.confidence_score,
                f.model_name,
                f.model_version,
                f.features_snapshot::text
                    AS features_snapshot_json,
                f.generated_at
            FROM demand_forecasts f
            JOIN locations l
              ON l.id = f.location_id
            JOIN campuses c
              ON c.id = l.campus_id
            WHERE c.organization_id = ?
              AND f.location_id = ?
              AND f.forecast_date = ?
              AND (
                    (
                        f.product_id IS NOT NULL
                        AND EXISTS (
                            SELECT 1
                            FROM products p
                            WHERE p.id = f.product_id
                              AND p.organization_id = ?
                        )
                    )
                    OR
                    (
                        f.ingredient_id IS NOT NULL
                        AND EXISTS (
                            SELECT 1
                            FROM ingredients i
                            WHERE i.id = f.ingredient_id
                              AND i.organization_id = ?
                        )
                    )
                  )
            ORDER BY
                f.generated_at DESC,
                f.id
            LIMIT ?
            """,
            (rs, rowNum) ->
                map(
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                    rs.getObject(
                        "location_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "product_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "ingredient_id",
                        UUID.class
                    ),
                    rs.getObject(
                        "forecast_date",
                        LocalDate.class
                    ),
                    rs.getObject(
                        "time_slot_id",
                        UUID.class
                    ),
                    rs.getBigDecimal(
                        "predicted_quantity"
                    ),
                    rs.getBigDecimal(
                        "confidence_score"
                    ),
                    rs.getString(
                        "model_name"
                    ),
                    rs.getString(
                        "model_version"
                    ),
                    rs.getString(
                        "features_snapshot_json"
                    ),
                    rs.getObject(
                        "generated_at",
                        OffsetDateTime.class
                    ),
                    false
                ),
            organizationId,
            locationId,
            forecastDate,
            organizationId,
            organizationId,
            safeLimit
        );
    }

    @Transactional(readOnly = true)
    public List<DemandForecastResponse> listForSubject(
        UUID organizationId,
        ForecastSubjectType subjectType,
        UUID subjectId,
        LocalDate fromDate,
        LocalDate toDate,
        int limit
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        if (subjectType == null) {

            throw new DemandForecastValidationException(
                "Forecast subject type is required."
            );
        }

        requireId(
            subjectId,
            "Forecast subject id"
        );

        if (
            fromDate == null
            || toDate == null
        ) {

            throw new DemandForecastValidationException(
                "Forecast date range is required."
            );
        }

        if (toDate.isBefore(fromDate)) {

            throw new DemandForecastValidationException(
                "Forecast end date must not be before start date."
            );
        }

        int safeLimit =
            safeLimit(
                limit
            );

        requireSubject(
            organizationId,
            subjectType,
            subjectId
        );

        if (subjectType == ForecastSubjectType.PRODUCT) {

            return jdbcTemplate.query(
                """
                SELECT
                    f.id,
                    f.location_id,
                    f.product_id,
                    f.ingredient_id,
                    f.forecast_date,
                    f.time_slot_id,
                    f.predicted_quantity,
                    f.confidence_score,
                    f.model_name,
                    f.model_version,
                    f.features_snapshot::text
                        AS features_snapshot_json,
                    f.generated_at
                FROM demand_forecasts f
                JOIN products p
                  ON p.id = f.product_id
                JOIN locations l
                  ON l.id = f.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE p.organization_id = ?
                  AND c.organization_id = ?
                  AND f.product_id = ?
                  AND f.forecast_date BETWEEN ? AND ?
                ORDER BY
                    f.forecast_date,
                    f.generated_at DESC,
                    f.id
                LIMIT ?
                """,
                (rs, rowNum) ->
                    mapRow(
                        rs,
                        false
                    ),
                organizationId,
                organizationId,
                subjectId,
                fromDate,
                toDate,
                safeLimit
            );
        }

        return jdbcTemplate.query(
            """
            SELECT
                f.id,
                f.location_id,
                f.product_id,
                f.ingredient_id,
                f.forecast_date,
                f.time_slot_id,
                f.predicted_quantity,
                f.confidence_score,
                f.model_name,
                f.model_version,
                f.features_snapshot::text
                    AS features_snapshot_json,
                f.generated_at
            FROM demand_forecasts f
            JOIN ingredients i
              ON i.id = f.ingredient_id
            JOIN locations l
              ON l.id = f.location_id
            JOIN campuses c
              ON c.id = l.campus_id
            WHERE i.organization_id = ?
              AND c.organization_id = ?
              AND f.ingredient_id = ?
              AND f.forecast_date BETWEEN ? AND ?
            ORDER BY
                f.forecast_date,
                f.generated_at DESC,
                f.id
            LIMIT ?
            """,
            (rs, rowNum) ->
                mapRow(
                    rs,
                    false
                ),
            organizationId,
            organizationId,
            subjectId,
            fromDate,
            toDate,
            safeLimit
        );
    }

    private DemandForecastResponse findById(
        UUID organizationId,
        UUID forecastId
    ) {

        List<DemandForecastResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    f.id,
                    f.location_id,
                    f.product_id,
                    f.ingredient_id,
                    f.forecast_date,
                    f.time_slot_id,
                    f.predicted_quantity,
                    f.confidence_score,
                    f.model_name,
                    f.model_version,
                    f.features_snapshot::text
                        AS features_snapshot_json,
                    f.generated_at
                FROM demand_forecasts f
                JOIN locations l
                  ON l.id = f.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE f.id = ?
                  AND c.organization_id = ?
                  AND (
                        (
                            f.product_id IS NOT NULL
                            AND EXISTS (
                                SELECT 1
                                FROM products p
                                WHERE p.id = f.product_id
                                  AND p.organization_id = ?
                            )
                        )
                        OR
                        (
                            f.ingredient_id IS NOT NULL
                            AND EXISTS (
                                SELECT 1
                                FROM ingredients i
                                WHERE i.id = f.ingredient_id
                                  AND i.organization_id = ?
                            )
                        )
                      )
                """,
                (rs, rowNum) ->
                    mapRow(
                        rs,
                        false
                    ),
                forecastId,
                organizationId,
                organizationId,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.get(
            0
        );
    }

    private DemandForecastResponse mapRow(
        java.sql.ResultSet rs,
        boolean replayed
    ) throws java.sql.SQLException {

        return map(
            rs.getObject(
                "id",
                UUID.class
            ),
            rs.getObject(
                "location_id",
                UUID.class
            ),
            rs.getObject(
                "product_id",
                UUID.class
            ),
            rs.getObject(
                "ingredient_id",
                UUID.class
            ),
            rs.getObject(
                "forecast_date",
                LocalDate.class
            ),
            rs.getObject(
                "time_slot_id",
                UUID.class
            ),
            rs.getBigDecimal(
                "predicted_quantity"
            ),
            rs.getBigDecimal(
                "confidence_score"
            ),
            rs.getString(
                "model_name"
            ),
            rs.getString(
                "model_version"
            ),
            rs.getString(
                "features_snapshot_json"
            ),
            rs.getObject(
                "generated_at",
                OffsetDateTime.class
            ),
            replayed
        );
    }

    private DemandForecastResponse map(
        UUID id,
        UUID locationId,
        UUID productId,
        UUID ingredientId,
        LocalDate forecastDate,
        UUID timeSlotId,
        BigDecimal predictedQuantity,
        BigDecimal confidenceScore,
        String modelName,
        String modelVersion,
        String featuresSnapshotJson,
        OffsetDateTime generatedAt,
        boolean replayed
    ) {

        ForecastSubjectType subjectType;
        UUID subjectId;

        if (
            productId != null
            && ingredientId == null
        ) {

            subjectType =
                ForecastSubjectType.PRODUCT;

            subjectId =
                productId;

        } else if (
            productId == null
            && ingredientId != null
        ) {

            subjectType =
                ForecastSubjectType.INGREDIENT;

            subjectId =
                ingredientId;

        } else {

            throw new DemandForecastConflictException(
                "Stored forecast subject is invalid."
            );
        }

        return new DemandForecastResponse(
            id,
            locationId,
            subjectType,
            subjectId,
            forecastDate,
            timeSlotId,
            predictedQuantity,
            confidenceScore,
            modelName,
            modelVersion,
            featuresSnapshotJson,
            generatedAt,
            replayed
        );
    }

    private DemandForecastResponse replay(
        DemandForecastResponse response
    ) {

        return new DemandForecastResponse(
            response.id(),
            response.locationId(),
            response.subjectType(),
            response.subjectId(),
            response.forecastDate(),
            response.timeSlotId(),
            response.predictedQuantity(),
            response.confidenceScore(),
            response.modelName(),
            response.modelVersion(),
            response.featuresSnapshotJson(),
            response.generatedAt(),
            true
        );
    }

    private boolean samePayload(
        DemandForecastResponse existing,
        CreateDemandForecastCommand command,
        String modelName,
        String modelVersion,
        String featuresSnapshotJson
    ) {

        return existing.locationId().equals(
            command.locationId()
        )
            && existing.subjectType()
                == command.subjectType()
            && existing.subjectId().equals(
                command.subjectId()
            )
            && existing.forecastDate().equals(
                command.forecastDate()
            )
            && Objects.equals(
                existing.timeSlotId(),
                command.timeSlotId()
            )
            && sameDecimal(
                existing.predictedQuantity(),
                command.predictedQuantity()
            )
            && sameDecimal(
                existing.confidenceScore(),
                command.confidenceScore()
            )
            && Objects.equals(
                existing.modelName(),
                modelName
            )
            && Objects.equals(
                existing.modelVersion(),
                modelVersion
            )
            && Objects.equals(
                existing.featuresSnapshotJson(),
                featuresSnapshotJson
            );
    }

    private boolean sameDecimal(
        BigDecimal left,
        BigDecimal right
    ) {

        if (
            left == null
            || right == null
        ) {
            return left == right;
        }

        return left.compareTo(
            right
        ) == 0;
    }

    private void validateCommand(
        CreateDemandForecastCommand command
    ) {

        if (command == null) {

            throw new DemandForecastValidationException(
                "Demand forecast command is required."
            );
        }

        requireId(
            command.locationId(),
            "Location id"
        );

        if (command.subjectType() == null) {

            throw new DemandForecastValidationException(
                "Forecast subject type is required."
            );
        }

        requireId(
            command.subjectId(),
            "Forecast subject id"
        );

        if (command.forecastDate() == null) {

            throw new DemandForecastValidationException(
                "Forecast date is required."
            );
        }

        if (command.predictedQuantity() == null) {

            throw new DemandForecastValidationException(
                "Predicted quantity is required."
            );
        }

        if (
            command.predictedQuantity()
                .compareTo(
                    ZERO
                ) < 0
        ) {

            throw new DemandForecastValidationException(
                "Predicted quantity must be greater than or equal to zero."
            );
        }

        BigDecimal confidence =
            command.confidenceScore();

        if (confidence != null) {

            boolean belowZero =
                confidence.compareTo(
                    ZERO
                ) < 0;

            boolean aboveOne =
                confidence.compareTo(
                    ONE
                ) > 0;

            if (
                belowZero
                || aboveOne
            ) {

                throw new DemandForecastValidationException(
                    "Confidence score must be between zero and one."
                );
            }
        }
    }

    private void lockLocation(
        UUID organizationId,
        UUID locationId
    ) {

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT l.id
                FROM locations l
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE l.id = ?
                  AND c.organization_id = ?
                FOR UPDATE OF l
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

            throw new DemandForecastNotFoundException(
                "Location does not exist."
            );
        }
    }

    private void requireLocation(
        UUID organizationId,
        UUID locationId
    ) {

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

            throw new DemandForecastNotFoundException(
                "Location does not exist."
            );
        }
    }

    private void lockSubject(
        UUID organizationId,
        ForecastSubjectType subjectType,
        UUID subjectId
    ) {

        if (subjectType == ForecastSubjectType.PRODUCT) {

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
                    subjectId,
                    organizationId
                );

            if (rows.isEmpty()) {

                throw new DemandForecastNotFoundException(
                    "Forecast product does not exist."
                );
            }

            return;
        }

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT id
                FROM ingredients
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                subjectId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new DemandForecastNotFoundException(
                "Forecast ingredient does not exist."
            );
        }
    }

    private void requireSubject(
        UUID organizationId,
        ForecastSubjectType subjectType,
        UUID subjectId
    ) {

        if (subjectType == ForecastSubjectType.PRODUCT) {

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
                    subjectId,
                    organizationId
                );

            if (rows.isEmpty()) {

                throw new DemandForecastNotFoundException(
                    "Forecast product does not exist."
                );
            }

            return;
        }

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT id
                FROM ingredients
                WHERE id = ?
                  AND organization_id = ?
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                subjectId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new DemandForecastNotFoundException(
                "Forecast ingredient does not exist."
            );
        }
    }

    private void validateOptionalTimeSlot(
        UUID organizationId,
        UUID timeSlotId
    ) {

        if (timeSlotId == null) {
            return;
        }

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT ts.id
                FROM time_slots ts
                JOIN locations l
                  ON l.id = ts.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE ts.id = ?
                  AND c.organization_id = ?
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                timeSlotId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new DemandForecastNotFoundException(
                "Time slot does not exist."
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

            throw new DemandForecastValidationException(
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

                throw new DemandForecastValidationException(
                    label + " must contain valid JSON."
                );
            }

            return canonical;

        } catch (DataAccessException exception) {

            throw new DemandForecastValidationException(
                label + " must contain valid JSON."
            );
        }
    }

    private String nullableTrimmed(
        String value,
        int maxLength,
        String label
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.length() > maxLength) {

            throw new DemandForecastValidationException(
                label + " exceeds maximum length."
            );
        }

        return normalized;
    }

    private int safeLimit(
        int limit
    ) {

        if (
            limit < 1
            || limit > MAX_LIST_LIMIT
        ) {

            throw new DemandForecastValidationException(
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

            throw new DemandForecastValidationException(
                label + " is required."
            );
        }
    }
}