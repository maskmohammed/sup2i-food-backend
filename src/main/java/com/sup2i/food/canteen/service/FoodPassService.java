package com.sup2i.food.canteen.service;

import com.sup2i.food.canteen.exception.CanteenErrorCode;
import com.sup2i.food.canteen.exception.CanteenException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FoodPassService {

    private final JdbcTemplate jdbcTemplate;

    public FoodPassService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public FoodPassContext resolveByFingerprint(
        UUID organizationId,
        String fingerprint
    ) {

        if (organizationId == null) {

            throw new IllegalArgumentException(
                "organizationId is required."
            );
        }

        if (
            fingerprint == null
            || fingerprint.isBlank()
        ) {

            throw new CanteenException(
                CanteenErrorCode.INVALID_QR,
                "Food Pass token is invalid."
            );
        }

        List<FoodPassContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    fp.id AS food_pass_id,
                    fp.student_id,
                    fp.status AS food_pass_status,
                    fp.expires_at AS food_pass_expires_at,

                    qc.status AS credential_status,
                    qc.expires_at AS credential_expires_at,

                    s.enrollment_status,
                    su.status AS student_user_status,

                    c.id AS campus_id,
                    c.timezone AS campus_timezone,
                    c.is_active AS campus_active

                FROM qr_credentials qc

                JOIN food_passes fp
                  ON fp.credential_id = qc.id
                 AND qc.subject_id = fp.id

                JOIN students s
                  ON s.id = fp.student_id

                JOIN users su
                  ON su.id = s.user_id

                JOIN campuses c
                  ON c.id = s.campus_id

                WHERE qc.token_hash = ?
                  AND qc.credential_type = 'FOOD_PASS'
                  AND su.organization_id = ?
                  AND c.organization_id = ?

                FOR UPDATE OF qc, fp
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new FoodPassContext(
                        resultSet.getObject(
                            "food_pass_id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "student_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "food_pass_status"
                        ),
                        resultSet.getObject(
                            "food_pass_expires_at",
                            OffsetDateTime.class
                        ),
                        resultSet.getString(
                            "credential_status"
                        ),
                        resultSet.getObject(
                            "credential_expires_at",
                            OffsetDateTime.class
                        ),
                        resultSet.getString(
                            "enrollment_status"
                        ),
                        resultSet.getString(
                            "student_user_status"
                        ),
                        resultSet.getObject(
                            "campus_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "campus_timezone"
                        ),
                        resultSet.getBoolean(
                            "campus_active"
                        )
                    ),
                fingerprint,
                organizationId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new CanteenException(
                CanteenErrorCode.INVALID_QR,
                "Food Pass token is unknown or invalid."
            );
        }

        if (rows.size() != 1) {

            throw new IllegalStateException(
                "Food Pass token lookup returned multiple rows."
            );
        }

        FoodPassContext context =
            rows.get(0);

        requireCredentialActive(
            context
        );

        requireFoodPassActive(
            context
        );

        requireStudentActive(
            context
        );

        if (
            context.campusTimezone() == null
            || context
                .campusTimezone()
                .isBlank()
        ) {

            throw new IllegalStateException(
                "Student campus timezone is missing."
            );
        }

        return context;
    }

    private void requireCredentialActive(
        FoodPassContext context
    ) {

        if (
            "REVOKED".equals(
                context.credentialStatus()
            )
        ) {

            throw new CanteenException(
                CanteenErrorCode.FOOD_PASS_REVOKED,
                "Food Pass credential has been revoked."
            );
        }

        boolean expiredByStatus =
            "EXPIRED".equals(
                context.credentialStatus()
            );

        boolean expiredByTime =
            context.credentialExpiresAt()
                != null
                && !OffsetDateTime
                    .now()
                    .isBefore(
                        context
                            .credentialExpiresAt()
                    );

        if (
            expiredByStatus
            || expiredByTime
        ) {

            throw new CanteenException(
                CanteenErrorCode.FOOD_PASS_EXPIRED,
                "Food Pass credential has expired."
            );
        }

        if (
            !"ACTIVE".equals(
                context.credentialStatus()
            )
        ) {

            throw new CanteenException(
                CanteenErrorCode.INVALID_QR,
                "Food Pass credential is not active."
            );
        }
    }

    private void requireFoodPassActive(
        FoodPassContext context
    ) {

        switch (
            context.foodPassStatus()
        ) {

            case "ACTIVE" -> {
            }

            case "BLOCKED" ->
                throw new CanteenException(
                    CanteenErrorCode.FOOD_PASS_BLOCKED,
                    "Food Pass is blocked."
                );

            case "LOST" ->
                throw new CanteenException(
                    CanteenErrorCode.FOOD_PASS_LOST,
                    "Food Pass has been declared lost."
                );

            case "REVOKED",
                 "REPLACED" ->
                throw new CanteenException(
                    CanteenErrorCode.FOOD_PASS_REVOKED,
                    "Food Pass is no longer valid."
                );

            case "EXPIRED" ->
                throw new CanteenException(
                    CanteenErrorCode.FOOD_PASS_EXPIRED,
                    "Food Pass has expired."
                );

            case "PENDING_ISSUE" ->
                throw new CanteenException(
                    CanteenErrorCode.INVALID_QR,
                    "Food Pass has not been issued yet."
                );

            default ->
                throw new IllegalStateException(
                    "Unsupported Food Pass status: "
                        + context.foodPassStatus()
                );
        }

        boolean expiredByTime =
            context.foodPassExpiresAt()
                != null
                && !OffsetDateTime
                    .now()
                    .isBefore(
                        context
                            .foodPassExpiresAt()
                    );

        if (expiredByTime) {

            throw new CanteenException(
                CanteenErrorCode.FOOD_PASS_EXPIRED,
                "Food Pass has expired."
            );
        }
    }

    private void requireStudentActive(
        FoodPassContext context
    ) {

        boolean enrollmentActive =
            "ACTIVE".equals(
                context.enrollmentStatus()
            );

        boolean userActive =
            "ACTIVE".equals(
                context.studentUserStatus()
            );

        if (
            !enrollmentActive
            || !userActive
            || !context.campusActive()
        ) {

            throw new CanteenException(
                CanteenErrorCode.STUDENT_INACTIVE,
                "Food Pass student is not active."
            );
        }
    }

    public record FoodPassContext(
        UUID foodPassId,
        UUID studentId,
        String foodPassStatus,
        OffsetDateTime foodPassExpiresAt,
        String credentialStatus,
        OffsetDateTime credentialExpiresAt,
        String enrollmentStatus,
        String studentUserStatus,
        UUID campusId,
        String campusTimezone,
        boolean campusActive
    ) {
    }
}
