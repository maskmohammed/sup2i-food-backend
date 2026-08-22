package com.sup2i.food.inventory.service;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.inventory.api.dto.CountInventoryItemRequest;
import com.sup2i.food.inventory.api.dto.CreateInventorySessionRequest;
import com.sup2i.food.inventory.api.dto.InventoryCountLineResponse;
import com.sup2i.food.inventory.api.dto.InventorySessionMutationResponse;
import com.sup2i.food.inventory.api.dto.InventorySessionResponse;
import com.sup2i.food.inventory.domain.InventoryCountLine;
import com.sup2i.food.inventory.domain.InventoryMovement;
import com.sup2i.food.inventory.domain.InventoryMovementType;
import com.sup2i.food.inventory.domain.InventorySession;
import com.sup2i.food.inventory.domain.InventorySessionStatus;
import com.sup2i.food.inventory.domain.StockBalance;
import com.sup2i.food.inventory.domain.StockBalanceId;
import com.sup2i.food.inventory.domain.StockItem;
import com.sup2i.food.inventory.domain.StockLocation;
import com.sup2i.food.inventory.exception.InventoryConflictException;
import com.sup2i.food.inventory.exception.InventoryNotFoundException;
import com.sup2i.food.inventory.exception.InventoryValidationException;
import com.sup2i.food.inventory.repository.InventoryCountLineRepository;
import com.sup2i.food.inventory.repository.InventoryMovementRepository;
import com.sup2i.food.inventory.repository.InventorySessionRepository;
import com.sup2i.food.inventory.repository.StockBalanceRepository;
import com.sup2i.food.inventory.repository.StockItemRepository;
import com.sup2i.food.inventory.repository.StockLocationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class InventoryCountService {

    private static final String
        REFERENCE_TYPE =
            "INVENTORY_SESSION";

    private static final Set<InventorySessionStatus>
        ACTIVE_STATUSES =
            Set.of(
                InventorySessionStatus.OPEN,
                InventorySessionStatus.COUNTING,
                InventorySessionStatus.COMPLETED
            );

    private final UserRepository userRepository;
    private final StockLocationRepository stockLocationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventorySessionRepository sessionRepository;
    private final InventoryCountLineRepository lineRepository;
    private final JdbcTemplate jdbcTemplate;

    public InventoryCountService(
        UserRepository userRepository,
        StockLocationRepository stockLocationRepository,
        StockItemRepository stockItemRepository,
        StockBalanceRepository stockBalanceRepository,
        InventoryMovementRepository movementRepository,
        InventorySessionRepository sessionRepository,
        InventoryCountLineRepository lineRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.userRepository =
            userRepository;

        this.stockLocationRepository =
            stockLocationRepository;

        this.stockItemRepository =
            stockItemRepository;

        this.stockBalanceRepository =
            stockBalanceRepository;

        this.movementRepository =
            movementRepository;

        this.sessionRepository =
            sessionRepository;

        this.lineRepository =
            lineRepository;

        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public InventorySessionMutationResponse
        createSession(
            UUID actorId,
            UUID sessionId,
            CreateInventorySessionRequest request
        ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        lockSessionId(
            sessionId
        );

        InventorySession existing =
            sessionRepository
                .findOwnedById(
                    sessionId,
                    organizationId
                )
                .orElse(null);

        if (existing != null) {

            if (
                !Objects.equals(
                    existing
                        .getStockLocation()
                        .getId(),
                    request.stockLocationId()
                )
                || !Objects.equals(
                    existing.getNotes(),
                    normalizeNullableText(
                        request.notes()
                    )
                )
            ) {
                throw new InventoryConflictException(
                    "Inventory session identifier is already used by another payload."
                );
            }

            return new InventorySessionMutationResponse(
                response(existing),
                true
            );
        }

        if (
            sessionRepository
                .existsById(sessionId)
        ) {
            throw new InventoryNotFoundException(
                "Inventory session does not exist."
            );
        }

        StockLocation stockLocation =
            stockLocationRepository
                .findByIdAndLocation_Campus_Organization_Id(
                    request.stockLocationId(),
                    organizationId
                )
                .orElseThrow(() ->
                    new InventoryNotFoundException(
                        "Stock location does not exist."
                    )
                );

        if (!stockLocation.isActive()) {
            throw new InventoryValidationException(
                "Stock location is not active."
            );
        }

        lockStockLocation(
            stockLocation.getId()
        );

        if (
            sessionRepository
                .existsByStockLocation_IdAndStatusIn(
                    stockLocation.getId(),
                    ACTIVE_STATUSES
                )
        ) {
            throw new InventoryConflictException(
                "An active inventory session already exists for this stock location."
            );
        }

        InventorySession session =
            new InventorySession(
                sessionId,
                stockLocation,
                actor,
                normalizeNullableText(
                    request.notes()
                )
            );

        try {
            session =
                sessionRepository
                    .saveAndFlush(session);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new InventoryConflictException(
                "Inventory session conflicts with an existing resource."
            );
        }

        return new InventorySessionMutationResponse(
            response(session),
            false
        );
    }

    @Transactional
    public InventoryCountLineResponse count(
        UUID actorId,
        UUID sessionId,
        UUID stockItemId,
        CountInventoryItemRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        InventorySession session =
            sessionForUpdate(
                sessionId,
                organizationId
            );

        if (
            session.getStatus()
                != InventorySessionStatus.OPEN
            && session.getStatus()
                != InventorySessionStatus.COUNTING
        ) {
            throw new InventoryConflictException(
                "Inventory session no longer accepts counts."
            );
        }

        StockItem stockItem =
            stockItemRepository
                .findByIdAndOrganization_Id(
                    stockItemId,
                    organizationId
                )
                .orElseThrow(() ->
                    new InventoryNotFoundException(
                        "Stock item does not exist."
                    )
                );

        InventoryCountLine line =
            lineRepository
                .findByInventorySession_IdAndStockItem_Id(
                    sessionId,
                    stockItemId
                )
                .orElse(null);

        if (line == null) {

            StockBalanceId balanceId =
                new StockBalanceId(
                    stockItemId,
                    session
                        .getStockLocation()
                        .getId()
                );

            StockBalance balance =
                stockBalanceRepository
                    .findLockedById(
                        balanceId
                    )
                    .orElse(null);

            BigDecimal physical =
                balance == null
                    ? BigDecimal.ZERO
                    : balance
                        .getPhysicalQuantity();

            BigDecimal reserved =
                balance == null
                    ? BigDecimal.ZERO
                    : balance
                        .getReservedQuantity();

            line =
                new InventoryCountLine(
                    session,
                    stockItem,
                    physical,
                    reserved
                );
        }

        line.count(
            request.countedQuantity(),
            actor,
            normalizeNullableText(
                request.reason()
            )
        );

        session.markCounting();

        line =
            lineRepository
                .saveAndFlush(line);

        sessionRepository.save(session);

        return lineResponse(line);
    }

    @Transactional
    public InventorySessionMutationResponse complete(
        UUID actorId,
        UUID sessionId
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        InventorySession session =
            sessionForUpdate(
                sessionId,
                organizationId
            );

        if (
            session.getStatus()
                == InventorySessionStatus.COMPLETED
        ) {
            return new InventorySessionMutationResponse(
                response(session),
                true
            );
        }

        if (
            session.getStatus()
                != InventorySessionStatus.COUNTING
        ) {
            throw new InventoryConflictException(
                "Only a counting inventory session can be completed."
            );
        }

        if (
            lineRepository
                .countByInventorySession_Id(
                    sessionId
                )
            == 0
        ) {
            throw new InventoryConflictException(
                "Inventory session contains no counted items."
            );
        }

        session.complete(actor);

        sessionRepository
            .saveAndFlush(session);

        return new InventorySessionMutationResponse(
            response(session),
            false
        );
    }

    @Transactional
    public InventorySessionMutationResponse apply(
        UUID actorId,
        UUID sessionId
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        InventorySession session =
            sessionForUpdate(
                sessionId,
                organizationId
            );

        if (
            session.getStatus()
                == InventorySessionStatus.APPLIED
        ) {
            return new InventorySessionMutationResponse(
                response(session),
                true
            );
        }

        if (
            session.getStatus()
                != InventorySessionStatus.COMPLETED
        ) {
            throw new InventoryConflictException(
                "Only a completed inventory session can be applied."
            );
        }

        List<InventoryCountLine> lines =
            lineRepository
                .findAllForSession(
                    sessionId
                );

        if (lines.isEmpty()) {
            throw new InventoryConflictException(
                "Inventory session contains no counted items."
            );
        }

        List<InventoryCountLine> ordered =
            lines.stream()
                .sorted(
                    Comparator.comparing(
                        line ->
                            line.getStockItem()
                                .getId()
                    )
                )
                .toList();

        try {

            for (
                InventoryCountLine line
                : ordered
            ) {

                StockItem stockItem =
                    line.getStockItem();

                StockBalanceId balanceId =
                    new StockBalanceId(
                        stockItem.getId(),
                        session
                            .getStockLocation()
                            .getId()
                    );

                stockBalanceRepository
                    .ensureExists(
                        stockItem.getId(),
                        session
                            .getStockLocation()
                            .getId()
                    );

                StockBalance balance =
                    stockBalanceRepository
                        .findLockedById(
                            balanceId
                        )
                        .orElseThrow(() ->
                            new InventoryConflictException(
                                "Stock balance could not be locked."
                            )
                        );

                if (
                    !sameDecimal(
                        balance
                            .getPhysicalQuantity(),
                        line
                            .getSystemPhysicalQuantity()
                    )
                    || !sameDecimal(
                        balance
                            .getReservedQuantity(),
                        line
                            .getSystemReservedQuantity()
                    )
                ) {
                    throw new InventoryConflictException(
                        "Stock changed after counting. Inventory session must be reviewed."
                    );
                }

                if (
                    line.getCountedQuantity()
                        .compareTo(
                            balance
                                .getReservedQuantity()
                        )
                    < 0
                ) {
                    throw new InventoryConflictException(
                        "Counted physical quantity cannot be lower than reserved quantity."
                    );
                }

                BigDecimal delta =
                    line.getCountedQuantity()
                        .subtract(
                            balance
                                .getPhysicalQuantity()
                        );

                if (delta.signum() == 0) {
                    continue;
                }

                balance.applyPhysicalDelta(
                    delta
                );

                InventoryMovement movement =
                    new InventoryMovement(
                        stockItem,
                        session
                            .getStockLocation(),
                        InventoryMovementType.ADJUSTMENT,
                        delta,
                        BigDecimal.ZERO,
                        stockItem.getBaseUnit(),
                        null,
                        REFERENCE_TYPE,
                        sessionId,
                        "Physical inventory adjustment",
                        line.getReason(),
                        actor
                    );

                movement =
                    movementRepository
                        .save(movement);

                line.attachAdjustment(
                    movement
                );

                stockBalanceRepository
                    .save(balance);

                lineRepository
                    .save(line);
            }

            session.apply(actor);

            sessionRepository
                .saveAndFlush(session);

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new InventoryConflictException(
                "Inventory application violates a stock invariant."
            );
        }

        return new InventorySessionMutationResponse(
            response(session),
            false
        );
    }

    @Transactional
    public InventorySessionMutationResponse cancel(
        UUID actorId,
        UUID sessionId
    ) {

        User actor =
            authenticatedUser(actorId);

        InventorySession session =
            sessionForUpdate(
                sessionId,
                actor
                    .getOrganization()
                    .getId()
            );

        if (
            session.getStatus()
                == InventorySessionStatus.CANCELLED
        ) {
            return new InventorySessionMutationResponse(
                response(session),
                true
            );
        }

        if (
            session.getStatus()
                == InventorySessionStatus.APPLIED
        ) {
            throw new InventoryConflictException(
                "Applied inventory session cannot be cancelled."
            );
        }

        session.cancel();

        sessionRepository
            .saveAndFlush(session);

        return new InventorySessionMutationResponse(
            response(session),
            false
        );
    }

    @Transactional(readOnly = true)
    public InventorySessionResponse findSession(
        UUID actorId,
        UUID sessionId
    ) {

        User actor =
            authenticatedUser(actorId);

        InventorySession session =
            sessionRepository
                .findOwnedById(
                    sessionId,
                    actor
                        .getOrganization()
                        .getId()
                )
                .orElseThrow(() ->
                    new InventoryNotFoundException(
                        "Inventory session does not exist."
                    )
                );

        return response(session);
    }

    private InventorySession sessionForUpdate(
        UUID sessionId,
        UUID organizationId
    ) {

        return sessionRepository
            .findOwnedByIdForUpdate(
                sessionId,
                organizationId
            )
            .orElseThrow(() ->
                new InventoryNotFoundException(
                    "Inventory session does not exist."
                )
            );
    }

    private InventorySessionResponse response(
        InventorySession session
    ) {

        List<InventoryCountLineResponse>
            lines =
                lineRepository
                    .findAllForSession(
                        session.getId()
                    )
                    .stream()
                    .map(
                        this::lineResponse
                    )
                    .toList();

        return new InventorySessionResponse(
            session.getId(),
            session.getStockLocation()
                .getId(),
            session.getStatus(),
            session.getStartedBy()
                .getId(),
            session.getStartedAt(),
            session.getCompletedBy() == null
                ? null
                : session
                    .getCompletedBy()
                    .getId(),
            session.getCompletedAt(),
            session.getAppliedBy() == null
                ? null
                : session
                    .getAppliedBy()
                    .getId(),
            session.getAppliedAt(),
            session.getNotes(),
            lines
        );
    }

    private InventoryCountLineResponse
        lineResponse(
            InventoryCountLine line
        ) {

        return new InventoryCountLineResponse(
            line.getId(),
            line.getStockItem()
                .getId(),
            line.getSystemPhysicalQuantity(),
            line.getSystemReservedQuantity(),
            line.getCountedQuantity(),
            line.getDifferenceQuantity(),
            line.getCountedBy() == null
                ? null
                : line
                    .getCountedBy()
                    .getId(),
            line.getCountedAt(),
            line.getAdjustmentMovement() == null
                ? null
                : line
                    .getAdjustmentMovement()
                    .getId(),
            line.getReason()
        );
    }

    private User authenticatedUser(
        UUID userId
    ) {

        return userRepository
            .findById(userId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user does not exist."
                )
            );
    }

    private void lockSessionId(
        UUID sessionId
    ) {

        long lockKey =
            sessionId
                .getMostSignificantBits()
            ^ sessionId
                .getLeastSignificantBits();

        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(?)",
            statement ->
                statement.setLong(
                    1,
                    lockKey
                ),
            (ResultSetExtractor<Void>)
                resultSet -> null
        );
    }

    private void lockStockLocation(
        UUID stockLocationId
    ) {

        long lockKey =
            stockLocationId
                .getMostSignificantBits()
            ^ stockLocationId
                .getLeastSignificantBits();

        jdbcTemplate.query(
            "SELECT pg_advisory_xact_lock(?)",
            statement ->
                statement.setLong(
                    1,
                    lockKey
                ),
            (ResultSetExtractor<Void>)
                resultSet -> null
        );
    }

    private boolean sameDecimal(
        BigDecimal left,
        BigDecimal right
    ) {

        return left.compareTo(right) == 0;
    }

    private String normalizeNullableText(
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