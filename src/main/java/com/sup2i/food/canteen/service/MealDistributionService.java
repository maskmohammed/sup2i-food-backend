package com.sup2i.food.canteen.service;

import com.sup2i.food.canteen.api.dto.MealDistributionRequest;
import com.sup2i.food.canteen.api.dto.MealUsageResponse;
import com.sup2i.food.canteen.exception.CanteenErrorCode;
import com.sup2i.food.canteen.exception.CanteenException;
import com.sup2i.food.scan.service.ScanTokenHasher;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class MealDistributionService {

    private static final int
        RESPONSE_STATUS_CREATED = 201;

    private static final String
        RESOURCE_TYPE =
            "MEAL_USAGE";

    private static final Duration
        IDEMPOTENCY_RETENTION =
            Duration.ofHours(24);

    private static final Set<String>
        ALLOWED_MEAL_TYPES =
            Set.of(
                "BREAKFAST",
                "LUNCH",
                "DINNER",
                "OTHER"
            );

    private final JdbcTemplate jdbcTemplate;

    private final JsonMapper objectMapper;

    private final ScanTokenHasher tokenHasher;

    private final FoodPassService
        foodPassService;

    private final MealEligibilityService
        mealEligibilityService;

    public MealDistributionService(
        JdbcTemplate jdbcTemplate,
        JsonMapper objectMapper,
        ScanTokenHasher tokenHasher,
        FoodPassService foodPassService,
        MealEligibilityService mealEligibilityService
    ) {
        this.jdbcTemplate =
            jdbcTemplate;

        this.objectMapper =
            objectMapper;

        this.tokenHasher =
            tokenHasher;

        this.foodPassService =
            foodPassService;

        this.mealEligibilityService =
            mealEligibilityService;
    }

    @Transactional
    public MealUsageResponse distribute(
        UUID actorId,
        String rawIdempotencyKey,
        MealDistributionRequest request
    ) {

        validateRequest(
            request
        );

        UUID organizationId =
            organizationId(
                actorId
            );

        String idempotencyKey =
            normalizeIdempotencyKey(
                rawIdempotencyKey
            );

        String rawToken =
            request
                .foodPassToken()
                .trim();

        String fingerprint =
            tokenHasher.hash(
                rawToken
            );

        String mealType =
            normalizeMealType(
                request.mealType()
            );

        String scope =
            "CANTEEN_DISTRIBUTE:"
                + organizationId;

        String requestHash =
            requestHash(
                actorId,
                fingerprint,
                mealType,
                request.menuId(),
                request.terminalId()
            );

        OffsetDateTime now =
            OffsetDateTime.now();

        lockIdempotency(
            scope,
            idempotencyKey
        );

        deleteExpiredIdempotency(
            scope,
            idempotencyKey,
            now
        );

        Optional<StoredIdempotency> stored =
            findStoredIdempotency(
                scope,
                idempotencyKey
            );

        if (stored.isPresent()) {

            StoredIdempotency replay =
                stored.get();

            validateReplay(
                replay,
                actorId,
                requestHash
            );

            return deserializeResponse(
                replay.responseBody()
            );
        }

        FoodPassService.FoodPassContext foodPass =
            foodPassService
                .resolveByFingerprint(
                    organizationId,
                    fingerprint
                );

        ZoneId campusZone;

        try {

            campusZone =
                ZoneId.of(
                    foodPass
                        .campusTimezone()
                );
        }
        catch (RuntimeException exception) {

            throw new IllegalStateException(
                "Student campus timezone is invalid.",
                exception
            );
        }

        LocalDate usageDate =
            LocalDate.now(
                campusZone
            );

        validateTerminal(
            organizationId,
            request.terminalId()
        );

        validateMenu(
            organizationId,
            foodPass.campusId(),
            request.menuId(),
            usageDate,
            mealType
        );

        lockUsageKey(
            foodPass.studentId(),
            usageDate,
            mealType
        );

        if (
            validUsageExists(
                foodPass.studentId(),
                usageDate,
                mealType
            )
        ) {

            throw new CanteenException(
                CanteenErrorCode.MEAL_ALREADY_USED,
                "Meal has already been distributed."
            );
        }

        MealEligibilityService.EligibilityDecision
            eligibility =
                mealEligibilityService
                    .requireStudentEligible(
                        organizationId,
                        foodPass.studentId(),
                        usageDate,
                        mealType,
                        request.menuId()
                    );

        Optional<ReservationRow> reservation =
            reservation(
                foodPass.studentId(),
                request.menuId()
            );

        UUID reservationId =
            null;

        if (reservation.isPresent()) {

            ReservationRow current =
                reservation.get();

            if (
                "RESERVED".equals(
                    current.status()
                )
            ) {

                reservationId =
                    current.id();
            }
            else if (
                eligibility
                    .reservationRequired()
            ) {

                throw new CanteenException(
                    CanteenErrorCode.MEAL_NOT_ALLOWED,
                    "Required canteen reservation is not available."
                );
            }
        }
        else if (
            eligibility
                .reservationRequired()
        ) {

            throw new CanteenException(
                CanteenErrorCode.MEAL_NOT_ALLOWED,
                "This meal requires a canteen reservation."
            );
        }

        UUID usageId =
            UUID.randomUUID();

        OffsetDateTime consumedAt =
            OffsetDateTime.now();

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO meal_usages (
                    id,
                    entitlement_id,
                    student_id,
                    meal_beneficiary_id,
                    menu_id,
                    usage_date,
                    meal_type,
                    food_pass_id,
                    consumed_at,
                    validated_by,
                    terminal_id,
                    status,
                    reservation_id
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    NULL,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    'VALID',
                    ?
                )
                ON CONFLICT (
                    student_id,
                    usage_date,
                    meal_type
                )
                WHERE status = 'VALID'
                DO NOTHING
                """,
                usageId,
                eligibility.entitlementId(),
                foodPass.studentId(),
                request.menuId(),
                usageDate,
                mealType,
                foodPass.foodPassId(),
                consumedAt,
                actorId,
                request.terminalId(),
                reservationId
            );

        if (inserted == 0) {

            throw new CanteenException(
                CanteenErrorCode.MEAL_ALREADY_USED,
                "Meal has already been distributed."
            );
        }

        if (inserted != 1) {

            throw new IllegalStateException(
                "Meal usage insert did not affect exactly one row."
            );
        }

        if (reservationId != null) {

            int reservationUpdated =
                jdbcTemplate.update(
                    """
                    UPDATE canteen_reservations
                    SET
                        status = 'CONSUMED',
                        consumed_at = ?
                    WHERE id = ?
                      AND status = 'RESERVED'
                    """,
                    consumedAt,
                    reservationId
                );

            if (reservationUpdated != 1) {

                throw new IllegalStateException(
                    "Canteen reservation consume transition failed."
                );
            }
        }

        Long remainingQuota =
            remainingAfterUse(
                eligibility.remainingQuota()
            );

        MealUsageResponse response =
            new MealUsageResponse(
                usageId,
                foodPass.studentId(),
                mealType,
                usageDate,
                consumedAt,
                remainingQuota
            );

        String responseBody =
            serializeResponse(
                response
            );

        auditSuccess(
            organizationId,
            actorId,
            request.terminalId(),
            usageId,
            responseBody
        );

        persistIdempotency(
            scope,
            idempotencyKey,
            actorId,
            requestHash,
            responseBody,
            usageId,
            now.plus(
                IDEMPOTENCY_RETENTION
            )
        );

        return response;
    }

    private void validateRequest(
        MealDistributionRequest request
    ) {

        if (request == null) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Distribution request is required."
            );
        }

        if (
            request.foodPassToken() == null
            || request
                .foodPassToken()
                .isBlank()
        ) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "foodPassToken is required."
            );
        }

        if (
            request.mealType() == null
            || request
                .mealType()
                .isBlank()
        ) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "mealType is required."
            );
        }
    }

    private String normalizeMealType(
        String rawMealType
    ) {

        String mealType =
            rawMealType.trim();

        if (
            !ALLOWED_MEAL_TYPES.contains(
                mealType
            )
        ) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "mealType is invalid."
            );
        }

        return mealType;
    }

    private UUID organizationId(
        UUID actorId
    ) {

        if (actorId == null) {

            throw new BadCredentialsException(
                "Authenticated user identifier is missing."
            );
        }

        List<UUID> organizations =
            jdbcTemplate.query(
                """
                SELECT u.organization_id
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "organization_id",
                        UUID.class
                    ),
                actorId
            );

        if (organizations.isEmpty()) {

            throw new BadCredentialsException(
                "Authenticated user does not exist or is inactive."
            );
        }

        if (organizations.size() != 1) {

            throw new IllegalStateException(
                "Authenticated organization lookup returned multiple rows."
            );
        }

        return organizations.get(0);
    }

    private void validateTerminal(
        UUID organizationId,
        UUID terminalId
    ) {

        if (terminalId == null) {
            return;
        }

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pos_terminals pt
                JOIN locations l
                  ON l.id = pt.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE pt.id = ?
                  AND c.organization_id = ?
                  AND pt.is_active = TRUE
                  AND l.is_active = TRUE
                  AND c.is_active = TRUE
                """,
                Long.class,
                terminalId,
                organizationId
            );

        if (
            count == null
            || count != 1
        ) {

            throw new CanteenException(
                CanteenErrorCode.RESOURCE_NOT_FOUND,
                "Distribution terminal does not exist."
            );
        }
    }

    private void validateMenu(
        UUID organizationId,
        UUID campusId,
        UUID menuId,
        LocalDate usageDate,
        String mealType
    ) {

        if (menuId == null) {
            return;
        }

        List<DistributionMenu> rows =
            jdbcTemplate.query(
                """
                SELECT
                    cm.menu_date,
                    cm.meal_type,
                    cm.status
                FROM canteen_menus cm
                JOIN locations l
                  ON l.id = cm.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE cm.id = ?
                  AND c.id = ?
                  AND c.organization_id = ?
                  AND l.is_active = TRUE
                  AND c.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new DistributionMenu(
                        resultSet.getObject(
                            "menu_date",
                            LocalDate.class
                        ),
                        resultSet.getString(
                            "meal_type"
                        ),
                        resultSet.getString(
                            "status"
                        )
                    ),
                menuId,
                campusId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new CanteenException(
                CanteenErrorCode.RESOURCE_NOT_FOUND,
                "Canteen menu does not exist for this campus."
            );
        }

        if (rows.size() != 1) {

            throw new IllegalStateException(
                "Distribution menu lookup returned multiple rows."
            );
        }

        DistributionMenu menu =
            rows.get(0);

        boolean published =
            "PUBLISHED".equals(
                menu.status()
            );

        boolean sameDate =
            usageDate.equals(
                menu.date()
            );

        boolean sameMealType =
            mealType.equals(
                menu.mealType()
            );

        if (
            !published
            || !sameDate
            || !sameMealType
        ) {

            throw new CanteenException(
                CanteenErrorCode.MEAL_NOT_ALLOWED,
                "Menu is not valid for the current meal service."
            );
        }
    }

    private Optional<ReservationRow> reservation(
        UUID studentId,
        UUID menuId
    ) {

        if (menuId == null) {
            return Optional.empty();
        }

        List<ReservationRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    status
                FROM canteen_reservations
                WHERE student_id = ?
                  AND meal_beneficiary_id IS NULL
                  AND menu_id = ?
                FOR UPDATE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new ReservationRow(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "status"
                        )
                    ),
                studentId,
                menuId
            );

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        if (rows.size() != 1) {

            throw new IllegalStateException(
                "Canteen reservation lookup returned multiple rows."
            );
        }

        return Optional.of(
            rows.get(0)
        );
    }

    private void lockUsageKey(
        UUID studentId,
        LocalDate usageDate,
        String mealType
    ) {

        String canonical =
            "MEAL_USAGE"
                + "\n"
                + studentId
                + "\n"
                + usageDate
                + "\n"
                + mealType;

        byte[] digest =
            sha256Bytes(
                canonical
            );

        long lockKey =
            ByteBuffer
                .wrap(digest)
                .getLong();

        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(?)",
            statement ->
                statement.setLong(
                    1,
                    lockKey
                ),
            (ResultSetExtractor<Void>)
                resultSet ->
                    null
        );
    }

    private boolean validUsageExists(
        UUID studentId,
        LocalDate usageDate,
        String mealType
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM meal_usages
                WHERE student_id = ?
                  AND meal_beneficiary_id IS NULL
                  AND usage_date = ?
                  AND meal_type = ?
                  AND status = 'VALID'
                """,
                Long.class,
                studentId,
                usageDate,
                mealType
            );

        return count != null
            && count > 0;
    }

    private Long remainingAfterUse(
        Long remainingBefore
    ) {

        if (remainingBefore == null) {
            return null;
        }

        long remaining =
            remainingBefore - 1;

        if (remaining < 0) {

            throw new IllegalStateException(
                "Remaining quota became negative."
            );
        }

        return remaining;
    }

    private String normalizeIdempotencyKey(
        String rawIdempotencyKey
    ) {

        if (rawIdempotencyKey == null) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Idempotency-Key header is required."
            );
        }

        String key =
            rawIdempotencyKey.trim();

        if (key.isBlank()) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Idempotency-Key header is required."
            );
        }

        if (key.length() < 8) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Idempotency-Key must contain at least 8 characters."
            );
        }

        if (key.length() > 160) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "Idempotency-Key cannot exceed 160 characters."
            );
        }

        return key;
    }

    private void lockIdempotency(
        String scope,
        String idempotencyKey
    ) {

        byte[] digest =
            sha256Bytes(
                "CANTEEN_IDEMPOTENCY"
                    + "\n"
                    + scope
                    + "\n"
                    + idempotencyKey
            );

        long lockKey =
            ByteBuffer
                .wrap(digest)
                .getLong();

        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(?)",
            statement ->
                statement.setLong(
                    1,
                    lockKey
                ),
            (ResultSetExtractor<Void>)
                resultSet ->
                    null
        );
    }

    private void deleteExpiredIdempotency(
        String scope,
        String idempotencyKey,
        OffsetDateTime now
    ) {

        jdbcTemplate.update(
            """
            DELETE FROM idempotency_records
            WHERE scope = ?
              AND idempotency_key = ?
              AND expires_at <= ?
            """,
            scope,
            idempotencyKey,
            now
        );
    }

    private Optional<StoredIdempotency>
        findStoredIdempotency(
            String scope,
            String idempotencyKey
        ) {

        List<StoredIdempotency> rows =
            jdbcTemplate.query(
                """
                SELECT
                    user_id,
                    request_hash,
                    response_status,
                    response_body::text
                        AS response_body,
                    resource_type,
                    resource_id
                FROM idempotency_records
                WHERE scope = ?
                  AND idempotency_key = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new StoredIdempotency(
                        resultSet.getObject(
                            "user_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "request_hash"
                        ),
                        resultSet.getInt(
                            "response_status"
                        ),
                        resultSet.getString(
                            "response_body"
                        ),
                        resultSet.getString(
                            "resource_type"
                        ),
                        resultSet.getObject(
                            "resource_id",
                            UUID.class
                        )
                    ),
                scope,
                idempotencyKey
            );

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        if (rows.size() != 1) {

            throw new IllegalStateException(
                "Distribution idempotency lookup returned multiple rows."
            );
        }

        return Optional.of(
            rows.get(0)
        );
    }

    private void validateReplay(
        StoredIdempotency stored,
        UUID actorId,
        String requestHash
    ) {

        boolean sameUser =
            actorId.equals(
                stored.userId()
            );

        boolean sameRequest =
            requestHash.equals(
                stored.requestHash()
            );

        boolean sameStatus =
            stored.responseStatus()
                == RESPONSE_STATUS_CREATED;

        boolean sameResourceType =
            RESOURCE_TYPE.equals(
                stored.resourceType()
            );

        boolean hasResource =
            stored.resourceId()
                != null;

        boolean hasResponse =
            stored.responseBody()
                != null;

        boolean validReplay =
            sameUser
                && sameRequest
                && sameStatus
                && sameResourceType
                && hasResource
                && hasResponse;

        if (!validReplay) {

            throw new CanteenException(
                CanteenErrorCode.IDEMPOTENCY_CONFLICT,
                "Idempotency-Key was already used for another request."
            );
        }
    }

    private void persistIdempotency(
        String scope,
        String idempotencyKey,
        UUID actorId,
        String requestHash,
        String responseBody,
        UUID usageId,
        OffsetDateTime expiresAt
    ) {

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO idempotency_records (
                    id,
                    idempotency_key,
                    scope,
                    user_id,
                    request_hash,
                    response_status,
                    response_body,
                    resource_type,
                    resource_id,
                    expires_at
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    CAST(? AS jsonb),
                    ?,
                    ?,
                    ?
                )
                """,
                UUID.randomUUID(),
                idempotencyKey,
                scope,
                actorId,
                requestHash,
                RESPONSE_STATUS_CREATED,
                responseBody,
                RESOURCE_TYPE,
                usageId,
                expiresAt
            );

        if (inserted != 1) {

            throw new IllegalStateException(
                "Distribution idempotency insert did not affect exactly one row."
            );
        }
    }

    private void auditSuccess(
        UUID organizationId,
        UUID actorId,
        UUID terminalId,
        UUID usageId,
        String responseBody
    ) {

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO audit_logs (
                    id,
                    organization_id,
                    user_id,
                    action,
                    resource_type,
                    resource_id,
                    after_data,
                    source,
                    terminal_id,
                    result
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    'FOOD_PASS_USED',
                    'MEAL_USAGE',
                    ?,
                    CAST(? AS jsonb),
                    'API',
                    ?,
                    'SUCCESS'
                )
                """,
                UUID.randomUUID(),
                organizationId,
                actorId,
                usageId,
                responseBody,
                terminalId
            );

        if (inserted != 1) {

            throw new IllegalStateException(
                "Meal distribution audit insert did not affect exactly one row."
            );
        }
    }

    private String serializeResponse(
        MealUsageResponse response
    ) {

        try {

            return objectMapper
                .writeValueAsString(
                    response
                );
        }
        catch (JacksonException exception) {

            throw new IllegalStateException(
                "Unable to serialize meal usage response.",
                exception
            );
        }
    }

    private MealUsageResponse deserializeResponse(
        String responseBody
    ) {

        try {

            return objectMapper
                .readerFor(
                    MealUsageResponse.class
                )
                .without(
                    DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
                )
                .readValue(
                    responseBody
                );
        }
        catch (JacksonException exception) {

            throw new IllegalStateException(
                "Unable to deserialize meal usage response.",
                exception
            );
        }
    }

    private String requestHash(
        UUID actorId,
        String fingerprint,
        String mealType,
        UUID menuId,
        UUID terminalId
    ) {

        String canonical =
            "CANTEEN_DISTRIBUTE"
                + "\n"
                + actorId
                + "\n"
                + fingerprint
                + "\n"
                + mealType
                + "\n"
                + nullableUuid(menuId)
                + "\n"
                + nullableUuid(terminalId);

        byte[] digest =
            sha256Bytes(
                canonical
            );

        StringBuilder hex =
            new StringBuilder(
                digest.length * 2
            );

        for (byte value : digest) {

            hex.append(
                String.format(
                    "%02x",
                    value & 0xff
                )
            );
        }

        return hex.toString();
    }

    private String nullableUuid(
        UUID value
    ) {

        if (value == null) {
            return "~";
        }

        return value.toString();
    }

    private byte[] sha256Bytes(
        String value
    ) {

        try {

            MessageDigest digest =
                MessageDigest.getInstance(
                    "SHA-256"
                );

            return digest.digest(
                value.getBytes(
                    StandardCharsets.UTF_8
                )
            );
        }
        catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                "SHA-256 is unavailable.",
                exception
            );
        }
    }

    private record DistributionMenu(
        LocalDate date,
        String mealType,
        String status
    ) {
    }

    private record ReservationRow(
        UUID id,
        String status
    ) {
    }

    private record StoredIdempotency(
        UUID userId,
        String requestHash,
        int responseStatus,
        String responseBody,
        String resourceType,
        UUID resourceId
    ) {
    }
}
