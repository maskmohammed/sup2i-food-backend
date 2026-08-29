package com.sup2i.food.kitchen.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.kitchen.api.KitchenTicketResponseMapper;
import com.sup2i.food.kitchen.api.dto.KitchenTicketResponse;
import com.sup2i.food.kitchen.domain.KitchenTicket;
import com.sup2i.food.kitchen.domain.KitchenTicketItem;
import com.sup2i.food.kitchen.exception.KitchenConflictException;
import com.sup2i.food.kitchen.exception.KitchenErrorCode;
import com.sup2i.food.kitchen.exception.KitchenNotFoundException;
import com.sup2i.food.kitchen.repository.KitchenTicketItemRepository;
import com.sup2i.food.kitchen.repository.KitchenTicketRepository;
import com.sup2i.food.order.domain.OrderItemMenuSelection;
import com.sup2i.food.order.repository.OrderItemMenuSelectionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class KitchenCommandService {

    private static final int IDEMPOTENCY_KEY_MIN_LENGTH = 8;

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 160;

    private static final int RESPONSE_STATUS_OK = 200;

    private static final String RESOURCE_TYPE =
        "KITCHEN_TICKET";

    /*
     * V022 requires a future expires_at value.
     * This duration is technical retention only.
     * It is not a Kitchen business timeout.
     */
    private static final Duration IDEMPOTENCY_RETENTION =
        Duration.ofHours(24);

    private final UserRepository userRepository;

    private final KitchenStartService startService;

    private final KitchenReadyService readyService;

    private final KitchenTicketRepository ticketRepository;

    private final KitchenTicketItemRepository
        ticketItemRepository;

    private final OrderItemMenuSelectionRepository
        menuSelectionRepository;

    private final KitchenTicketResponseMapper responseMapper;

    private final JdbcTemplate jdbcTemplate;

    private final JsonMapper objectMapper;

    public KitchenCommandService(
        UserRepository userRepository,
        KitchenStartService startService,
        KitchenReadyService readyService,
        KitchenTicketRepository ticketRepository,
        KitchenTicketItemRepository ticketItemRepository,
        OrderItemMenuSelectionRepository menuSelectionRepository,
        KitchenTicketResponseMapper responseMapper,
        JdbcTemplate jdbcTemplate,
        JsonMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.startService = startService;
        this.readyService = readyService;
        this.ticketRepository = ticketRepository;
        this.ticketItemRepository = ticketItemRepository;
        this.menuSelectionRepository = menuSelectionRepository;
        this.responseMapper = responseMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KitchenTicketResponse startTicket(
        UUID actorId,
        UUID ticketId,
        String idempotencyKey
    ) {

        return execute(
            actorId,
            ticketId,
            idempotencyKey,
            Operation.START
        );
    }

    @Transactional
    public KitchenTicketResponse markReady(
        UUID actorId,
        UUID ticketId,
        String idempotencyKey
    ) {

        return execute(
            actorId,
            ticketId,
            idempotencyKey,
            Operation.READY
        );
    }

    private KitchenTicketResponse execute(
        UUID actorId,
        UUID ticketId,
        String rawIdempotencyKey,
        Operation operation
    ) {

        Objects.requireNonNull(
            actorId,
            "actorId"
        );

        Objects.requireNonNull(
            ticketId,
            "ticketId"
        );

        Objects.requireNonNull(
            operation,
            "operation"
        );

        User actor =
            authenticatedUser(
                actorId
            );

        if (actor.getOrganization() == null) {
            throw new BadCredentialsException(
                "Authenticated user has no organization."
            );
        }

        UUID organizationId =
            actor
                .getOrganization()
                .getId();

        String idempotencyKey =
            normalizeIdempotencyKey(
                rawIdempotencyKey
            );

        String scope =
            operation.scope(
                organizationId
            );

        String requestHash =
            requestHash(
                operation,
                ticketId
            );

        OffsetDateTime now =
            OffsetDateTime.now();

        /*
         * Same tenant + operation + key is serialized before
         * lookup and before any Kitchen workflow mutation.
         */
        lockIdempotency(
            scope,
            idempotencyKey
        );

        deleteExpired(
            scope,
            idempotencyKey,
            now
        );

        Optional<StoredIdempotency> stored =
            findStored(
                scope,
                idempotencyKey
            );

        if (stored.isPresent()) {

            StoredIdempotency replay =
                stored.get();

            validateReplay(
                replay,
                actorId,
                ticketId,
                requestHash
            );

            /*
             * Replay returns the exact original successful
             * Kitchen response and never runs workflow again.
             */
            return deserializeResponse(
                replay.responseBody()
            );
        }

        switch (operation) {

            case START ->
                startService.startTicket(
                    actorId,
                    ticketId,
                    now
                );

            case READY ->
                readyService.markReady(
                    actorId,
                    ticketId,
                    now
                );
        }

        KitchenTicketResponse response =
            loadResponse(
                organizationId,
                ticketId
            );

        String responseBody =
            serializeResponse(
                response
            );

        /*
         * Workflow mutation and durable idempotency success
         * record share the same local transaction.
         */
        insertStored(
            scope,
            idempotencyKey,
            actorId,
            requestHash,
            responseBody,
            ticketId,
            now.plus(
                IDEMPOTENCY_RETENTION
            )
        );

        return response;
    }

    private String normalizeIdempotencyKey(
        String rawIdempotencyKey
    ) {

        if (rawIdempotencyKey == null) {
            throw new IllegalArgumentException(
                "Idempotency-Key header is required."
            );
        }

        String idempotencyKey =
            rawIdempotencyKey.trim();

        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                "Idempotency-Key header is required."
            );
        }

        if (
            idempotencyKey.length()
                < IDEMPOTENCY_KEY_MIN_LENGTH
        ) {
            throw new IllegalArgumentException(
                "Idempotency-Key must contain at least 8 characters."
            );
        }

        if (
            idempotencyKey.length()
                > IDEMPOTENCY_KEY_MAX_LENGTH
        ) {
            throw new IllegalArgumentException(
                "Idempotency-Key cannot exceed 160 characters."
            );
        }

        return idempotencyKey;
    }

    private void lockIdempotency(
        String scope,
        String idempotencyKey
    ) {

        byte[] digest =
            sha256Bytes(
                "KITCHEN_IDEMPOTENCY"
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

    private void deleteExpired(
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
            statement -> {
                statement.setString(
                    1,
                    scope
                );

                statement.setString(
                    2,
                    idempotencyKey
                );

                statement.setObject(
                    3,
                    now
                );
            }
        );
    }

    private Optional<StoredIdempotency> findStored(
        String scope,
        String idempotencyKey
    ) {

        List<StoredIdempotency> records =
            jdbcTemplate.query(
                """
                SELECT
                    user_id,
                    request_hash,
                    response_status,
                    response_body::text AS response_body,
                    resource_type,
                    resource_id
                FROM idempotency_records
                WHERE scope = ?
                  AND idempotency_key = ?
                """,
                statement -> {
                    statement.setString(
                        1,
                        scope
                    );

                    statement.setString(
                        2,
                        idempotencyKey
                    );
                },
                (resultSet, rowNumber) ->
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
                    )
            );

        if (records.size() > 1) {
            throw new KitchenConflictException(
                KitchenErrorCode.CONCURRENT_MODIFICATION,
                "Multiple Kitchen idempotency records exist for one key."
            );
        }

        if (records.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(
            records.get(0)
        );
    }

    private void validateReplay(
        StoredIdempotency stored,
        UUID actorId,
        UUID ticketId,
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
                == RESPONSE_STATUS_OK;

        boolean hasResponse =
            stored.responseBody()
                != null;

        boolean sameResourceType =
            RESOURCE_TYPE.equals(
                stored.resourceType()
            );

        boolean sameResource =
            ticketId.equals(
                stored.resourceId()
            );

        boolean sameIdentity =
            sameUser
                && sameRequest
                && sameStatus
                && hasResponse
                && sameResourceType
                && sameResource;

        if (!sameIdentity) {
            throw new KitchenConflictException(
                KitchenErrorCode.IDEMPOTENCY_CONFLICT,
                "Idempotency key is already used with a different Kitchen request."
            );
        }
    }

    private void insertStored(
        String scope,
        String idempotencyKey,
        UUID actorId,
        String requestHash,
        String responseBody,
        UUID ticketId,
        OffsetDateTime expiresAt
    ) {

        int inserted =
            jdbcTemplate.update(
                """
                INSERT INTO idempotency_records (
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
                    CAST(? AS jsonb),
                    ?,
                    ?,
                    ?
                )
                """,
                statement -> {
                    statement.setString(
                        1,
                        idempotencyKey
                    );

                    statement.setString(
                        2,
                        scope
                    );

                    statement.setObject(
                        3,
                        actorId
                    );

                    statement.setString(
                        4,
                        requestHash
                    );

                    statement.setInt(
                        5,
                        RESPONSE_STATUS_OK
                    );

                    statement.setString(
                        6,
                        responseBody
                    );

                    statement.setString(
                        7,
                        RESOURCE_TYPE
                    );

                    statement.setObject(
                        8,
                        ticketId
                    );

                    statement.setObject(
                        9,
                        expiresAt
                    );
                }
            );

        if (inserted != 1) {
            throw new KitchenConflictException(
                KitchenErrorCode.CONCURRENT_MODIFICATION,
                "Kitchen idempotency record could not be persisted."
            );
        }
    }

    private KitchenTicketResponse loadResponse(
        UUID organizationId,
        UUID ticketId
    ) {

        KitchenTicket ticket =
            ticketRepository
                .findOwnedById(
                    ticketId,
                    organizationId
                )
                .orElseThrow(() ->
                    new KitchenNotFoundException(
                        KitchenErrorCode.KITCHEN_TICKET_NOT_FOUND,
                        "Kitchen ticket does not exist."
                    )
                );

        if (
            ticket.getOrder() == null
            || ticket.getOrder().getId() == null
        ) {
            throw new KitchenConflictException(
                KitchenErrorCode.CONCURRENT_MODIFICATION,
                "Kitchen ticket has no valid parent order."
            );
        }

        List<KitchenTicketItem> lines =
            ticketItemRepository
                .findAllByTicketIds(
                    List.of(
                        ticketId
                    )
                );

        if (lines.isEmpty()) {
            throw new KitchenConflictException(
                KitchenErrorCode.CONCURRENT_MODIFICATION,
                "Kitchen ticket has no routed lines."
            );
        }

        List<OrderItemMenuSelection> selections =
            menuSelectionRepository
                .findAllByOrderIds(
                    List.of(
                        ticket
                            .getOrder()
                            .getId()
                    )
                );

        Map<UUID, OrderItemMenuSelection> selectionsById =
            new LinkedHashMap<>();

        for (
            OrderItemMenuSelection selection
            : selections
        ) {

            OrderItemMenuSelection previous =
                selectionsById.put(
                    selection.getId(),
                    selection
                );

            if (previous != null) {
                throw new KitchenConflictException(
                    KitchenErrorCode.CONCURRENT_MODIFICATION,
                    "Duplicate Kitchen menu selection identifier."
                );
            }
        }

        return responseMapper.toResponse(
            ticket,
            lines,
            selectionsById
        );
    }

    private String serializeResponse(
        KitchenTicketResponse response
    ) {

        try {

            return objectMapper.writeValueAsString(
                response
            );

        } catch (
            JacksonException exception
        ) {

            throw new KitchenConflictException(
                KitchenErrorCode.CONCURRENT_MODIFICATION,
                "Kitchen idempotency response could not be serialized."
            );
        }
    }

    private KitchenTicketResponse deserializeResponse(
        String responseBody
    ) {

        try {

            return objectMapper
                .readerFor(
                    KitchenTicketResponse.class
                )
                .without(
                    DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
                )
                .readValue(
                    responseBody
                );

        } catch (
            JacksonException exception
        ) {

            throw new KitchenConflictException(
                KitchenErrorCode.CONCURRENT_MODIFICATION,
                "Stored Kitchen idempotency response is invalid."
            );
        }
    }

    private User authenticatedUser(
        UUID actorId
    ) {

        return userRepository
            .findById(
                actorId
            )
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user does not exist."
                )
            );
    }

    private String requestHash(
        Operation operation,
        UUID ticketId
    ) {

        return sha256Hex(
            operation.name()
                + "\n"
                + ticketId
        );
    }

    private String sha256Hex(
        String value
    ) {

        byte[] digest =
            sha256Bytes(
                value
            );

        StringBuilder hex =
            new StringBuilder(
                digest.length * 2
            );

        for (byte item : digest) {

            int unsigned =
                item & 0xff;

            hex.append(
                Character.forDigit(
                    unsigned >>> 4,
                    16
                )
            );

            hex.append(
                Character.forDigit(
                    unsigned & 0x0f,
                    16
                )
            );
        }

        return hex.toString();
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

        } catch (
            NoSuchAlgorithmException exception
        ) {

            throw new IllegalStateException(
                "SHA-256 is unavailable.",
                exception
            );
        }
    }

    private enum Operation {

        START(
            "KITCHEN_START"
        ),

        READY(
            "KITCHEN_READY"
        );

        private final String scopePrefix;

        Operation(
            String scopePrefix
        ) {
            this.scopePrefix =
                scopePrefix;
        }

        private String scope(
            UUID organizationId
        ) {

            return scopePrefix
                + ":"
                + organizationId;
        }
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
