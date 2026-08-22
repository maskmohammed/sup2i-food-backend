package com.sup2i.food.inventory.service;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.inventory.api.dto.StockAlertMutationResponse;
import com.sup2i.food.inventory.api.dto.StockAlertReconcileResponse;
import com.sup2i.food.inventory.api.dto.StockAlertResponse;
import com.sup2i.food.inventory.domain.StockAlert;
import com.sup2i.food.inventory.domain.StockAlertSeverity;
import com.sup2i.food.inventory.domain.StockAlertStatus;
import com.sup2i.food.inventory.domain.StockAlertType;
import com.sup2i.food.inventory.domain.StockBalance;
import com.sup2i.food.inventory.domain.StockItem;
import com.sup2i.food.inventory.domain.StockLocation;
import com.sup2i.food.inventory.domain.StockLot;
import com.sup2i.food.inventory.exception.InventoryConflictException;
import com.sup2i.food.inventory.exception.InventoryNotFoundException;
import com.sup2i.food.inventory.repository.StockAlertRepository;
import com.sup2i.food.inventory.repository.StockBalanceRepository;
import com.sup2i.food.inventory.repository.StockItemRepository;
import com.sup2i.food.inventory.repository.StockLocationRepository;
import com.sup2i.food.inventory.repository.StockLotRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryAlertService {

    private static final int
        EXPIRY_WARNING_DAYS =
            7;

    private static final BigDecimal
        CRITICAL_STOCK_RATIO =
            new BigDecimal("0.25");

    private static final List<StockAlertStatus>
        ACTIVE_STATUSES =
            List.of(
                StockAlertStatus.OPEN,
                StockAlertStatus.ACKNOWLEDGED
            );

    private static final List<StockAlertType>
        MANAGED_TYPES =
            List.of(
                StockAlertType.LOW_STOCK,
                StockAlertType.OUT_OF_STOCK,
                StockAlertType.EXPIRY
            );

    private final UserRepository userRepository;
    private final StockAlertRepository alertRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockLotRepository lotRepository;
    private final StockItemRepository stockItemRepository;
    private final StockLocationRepository stockLocationRepository;
    private final JdbcTemplate jdbcTemplate;

    public InventoryAlertService(
        UserRepository userRepository,
        StockAlertRepository alertRepository,
        StockBalanceRepository balanceRepository,
        StockLotRepository lotRepository,
        StockItemRepository stockItemRepository,
        StockLocationRepository stockLocationRepository,
        JdbcTemplate jdbcTemplate
    ) {
        this.userRepository =
            userRepository;

        this.alertRepository =
            alertRepository;

        this.balanceRepository =
            balanceRepository;

        this.lotRepository =
            lotRepository;

        this.stockItemRepository =
            stockItemRepository;

        this.stockLocationRepository =
            stockLocationRepository;

        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public StockAlertReconcileResponse reconcile(
        UUID actorId
    ) {

        User actor =
            authenticatedUser(actorId);

        return reconcileOrganization(
            actor.getOrganization()
                .getId()
        );
    }

    @Transactional
    public StockAlertReconcileResponse
        reconcileOrganization(
            UUID organizationId
        ) {

        lockOrganization(
            organizationId
        );

        OffsetDateTime now =
            OffsetDateTime.now();

        Map<AlertKey, AlertDefinition> desired =
            new HashMap<>();

        buildStockLevelAlerts(
            organizationId,
            desired
        );

        buildExpiryAlerts(
            organizationId,
            now,
            desired
        );

        List<StockAlert> active =
            alertRepository
                .findManagedActiveForUpdate(
                    organizationId,
                    ACTIVE_STATUSES,
                    MANAGED_TYPES
                );

        Map<AlertKey, StockAlert> activeByKey =
            new HashMap<>();

        int resolved = 0;

        for (
            StockAlert alert
            : active
        ) {

            AlertKey key =
                key(alert);

            StockAlert previous =
                activeByKey.putIfAbsent(
                    key,
                    alert
                );

            if (previous != null) {

                alert.resolve(now);

                resolved++;
            }
        }

        int retained = 0;

        for (
            Map.Entry<AlertKey, StockAlert> entry
            : activeByKey.entrySet()
        ) {

            AlertDefinition definition =
                desired.remove(
                    entry.getKey()
                );

            StockAlert alert =
                entry.getValue();

            if (definition == null) {

                alert.resolve(now);

                resolved++;

                continue;
            }

            /*
             * Important:
             * refresh() ne réinitialise jamais ACKNOWLEDGED.
             *
             * Une alerte acquittée reste acquittée tant que
             * la condition existe, mais observed_value et
             * severity continuent à évoluer.
             */
            alert.refresh(
                definition.thresholdValue(),
                definition.observedValue(),
                definition.severity()
            );

            retained++;
        }

        int created = 0;

        for (
            AlertDefinition definition
            : desired.values()
        ) {

            alertRepository.save(
                new StockAlert(
                    definition.stockItem(),
                    definition.stockLocation(),
                    definition.alertType(),
                    definition.thresholdValue(),
                    definition.observedValue(),
                    definition.lot(),
                    definition.severity(),
                    now
                )
            );

            created++;
        }

        alertRepository.flush();

        return new StockAlertReconcileResponse(
            created,
            resolved,
            retained,
            created + retained,
            EXPIRY_WARNING_DAYS
        );
    }

    @Transactional(readOnly = true)
    public List<StockAlertResponse> search(
        UUID actorId,
        StockAlertStatus status,
        StockAlertType alertType,
        UUID stockLocationId,
        UUID stockItemId
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization()
                .getId();

        validateFilters(
            organizationId,
            stockLocationId,
            stockItemId
        );

        return alertRepository
            .search(
                organizationId,
                status,
                alertType,
                stockLocationId,
                stockItemId
            )
            .stream()
            .map(
                this::response
            )
            .toList();
    }

    @Transactional(readOnly = true)
    public StockAlertResponse find(
        UUID actorId,
        UUID alertId
    ) {

        User actor =
            authenticatedUser(actorId);

        StockAlert alert =
            alertRepository
                .findOwnedById(
                    alertId,
                    actor.getOrganization()
                        .getId()
                )
                .orElseThrow(() ->
                    new InventoryNotFoundException(
                        "Stock alert does not exist."
                    )
                );

        return response(alert);
    }

    @Transactional
    public StockAlertMutationResponse acknowledge(
        UUID actorId,
        UUID alertId
    ) {

        User actor =
            authenticatedUser(actorId);

        StockAlert alert =
            alertRepository
                .findOwnedByIdForUpdate(
                    alertId,
                    actor.getOrganization()
                        .getId()
                )
                .orElseThrow(() ->
                    new InventoryNotFoundException(
                        "Stock alert does not exist."
                    )
                );

        if (
            alert.getStatus()
                == StockAlertStatus.RESOLVED
        ) {
            throw new InventoryConflictException(
                "Resolved stock alert cannot be acknowledged."
            );
        }

        if (
            alert.getStatus()
                == StockAlertStatus.ACKNOWLEDGED
        ) {
            return new StockAlertMutationResponse(
                response(alert),
                true
            );
        }

        alert.acknowledge(
            actor,
            OffsetDateTime.now()
        );

        alertRepository
            .saveAndFlush(alert);

        return new StockAlertMutationResponse(
            response(alert),
            false
        );
    }

    private void buildStockLevelAlerts(
        UUID organizationId,
        Map<AlertKey, AlertDefinition> desired
    ) {

        List<StockBalance> balances =
            balanceRepository
                .findAllForAlertReconciliation(
                    organizationId
                );

        for (
            StockBalance balance
            : balances
        ) {

            StockItem item =
                balance.getStockItem();

            StockLocation location =
                balance.getStockLocation();

            if (!location.isActive()) {
                continue;
            }

            if (!isTrackingEnabled(item)) {
                continue;
            }

            BigDecimal available =
                balance.getAvailableQuantity();

            /*
             * OUT_OF_STOCK domine toujours LOW_STOCK.
             *
             * Grâce à la clé incluant alertType,
             * une ancienne LOW_STOCK sera résolue
             * automatiquement lorsqu'un OUT_OF_STOCK
             * devient désiré.
             */
            if (available.signum() <= 0) {

                AlertDefinition definition =
                    new AlertDefinition(
                        item,
                        location,
                        null,
                        StockAlertType.OUT_OF_STOCK,
                        StockAlertSeverity.OUT_OF_STOCK,
                        item.getLowStockThreshold(),
                        available
                    );

                desired.put(
                    key(definition),
                    definition
                );

                continue;
            }

            BigDecimal threshold =
                item.getLowStockThreshold();

            if (threshold == null) {
                continue;
            }

            if (
                available.compareTo(
                    threshold
                ) > 0
            ) {
                continue;
            }

            BigDecimal criticalBoundary =
                threshold.multiply(
                    CRITICAL_STOCK_RATIO
                );

            StockAlertSeverity severity =
                available.compareTo(
                    criticalBoundary
                ) <= 0
                    ? StockAlertSeverity.CRITICAL
                    : StockAlertSeverity.LOW;

            AlertDefinition definition =
                new AlertDefinition(
                    item,
                    location,
                    null,
                    StockAlertType.LOW_STOCK,
                    severity,
                    threshold,
                    available
                );

            desired.put(
                key(definition),
                definition
            );
        }
    }

    private void buildExpiryAlerts(
        UUID organizationId,
        OffsetDateTime now,
        Map<AlertKey, AlertDefinition> desired
    ) {

        OffsetDateTime horizon =
            now.plusDays(
                EXPIRY_WARNING_DAYS
            );

        List<StockLot> lots =
            lotRepository.search(
                organizationId,
                null,
                null,
                true
            );

        for (
            StockLot lot
            : lots
        ) {

            StockItem item =
                lot.getStockItem();

            StockLocation location =
                lot.getStockLocation();

            if (!location.isActive()) {
                continue;
            }

            if (!isTrackingEnabled(item)) {
                continue;
            }

            if (!item.isTrackExpiry()) {
                continue;
            }

            /*
             * search(... remainingOnly=true) garantit déjà
             * quantityRemaining > 0.
             */
            OffsetDateTime expiresAt =
                lot.getExpiresAt();

            if (expiresAt == null) {
                continue;
            }

            if (expiresAt.isAfter(horizon)) {
                continue;
            }

            StockAlertSeverity severity =
                expirySeverity(
                    now,
                    expiresAt
                );

            BigDecimal observedDays =
                daysBetween(
                    now,
                    expiresAt
                );

            AlertDefinition definition =
                new AlertDefinition(
                    item,
                    location,
                    lot,
                    StockAlertType.EXPIRY,
                    severity,
                    BigDecimal.valueOf(
                        EXPIRY_WARNING_DAYS
                    ),
                    observedDays
                );

            desired.put(
                key(definition),
                definition
            );
        }
    }

    private StockAlertSeverity expirySeverity(
        OffsetDateTime now,
        OffsetDateTime expiresAt
    ) {

        if (!expiresAt.isAfter(now)) {
            return StockAlertSeverity.CRITICAL;
        }

        if (
            !expiresAt.isAfter(
                now.plusHours(24)
            )
        ) {
            return StockAlertSeverity.CRITICAL;
        }

        if (
            !expiresAt.isAfter(
                now.plusDays(3)
            )
        ) {
            return StockAlertSeverity.LOW;
        }

        return StockAlertSeverity.INFO;
    }

    private void validateFilters(
        UUID organizationId,
        UUID stockLocationId,
        UUID stockItemId
    ) {

        if (stockLocationId != null) {

            stockLocationRepository
                .findByIdAndLocation_Campus_Organization_Id(
                    stockLocationId,
                    organizationId
                )
                .orElseThrow(() ->
                    new InventoryNotFoundException(
                        "Stock location does not exist."
                    )
                );
        }

        if (stockItemId != null) {

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
        }
    }

    private StockAlertResponse response(
        StockAlert alert
    ) {

        return new StockAlertResponse(
            alert.getId(),
            alert.getStockItem()
                .getId(),
            alert.getStockLocation() == null
                ? null
                : alert
                    .getStockLocation()
                    .getId(),
            alert.getAlertType(),
            alert.getStatus(),
            alert.getSeverity(),
            alert.getThresholdValue(),
            alert.getObservedValue(),
            alert.getLot() == null
                ? null
                : alert
                    .getLot()
                    .getId(),
            alert.getDetectedAt(),
            alert.getAcknowledgedBy() == null
                ? null
                : alert
                    .getAcknowledgedBy()
                    .getId(),
            alert.getAcknowledgedAt(),
            alert.getResolvedAt()
        );
    }

    private boolean isTrackingEnabled(
        StockItem stockItem
    ) {

        if (
            stockItem.getProduct() != null
        ) {
            return stockItem
                .getProduct()
                .isTrackStock();
        }

        if (
            stockItem.getVariant() != null
        ) {
            return stockItem
                .getVariant()
                .getProduct()
                .isTrackStock();
        }

        if (
            stockItem.getIngredient() != null
        ) {
            return stockItem
                .getIngredient()
                .isTrackStock();
        }

        return false;
    }

    private BigDecimal daysBetween(
        OffsetDateTime from,
        OffsetDateTime to
    ) {

        long seconds =
            Duration.between(
                from.toInstant(),
                to.toInstant()
            ).getSeconds();

        return BigDecimal
            .valueOf(seconds)
            .divide(
                BigDecimal.valueOf(
                    86_400L
                ),
                3,
                RoundingMode.HALF_UP
            );
    }

    private AlertKey key(
        StockAlert alert
    ) {

        return new AlertKey(
            alert.getAlertType(),
            alert.getStockItem()
                .getId(),
            alert.getStockLocation() == null
                ? null
                : alert
                    .getStockLocation()
                    .getId(),
            alert.getLot() == null
                ? null
                : alert
                    .getLot()
                    .getId()
        );
    }

    private AlertKey key(
        AlertDefinition definition
    ) {

        return new AlertKey(
            definition.alertType(),
            definition.stockItem()
                .getId(),
            definition.stockLocation()
                .getId(),
            definition.lot() == null
                ? null
                : definition
                    .lot()
                    .getId()
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

    private void lockOrganization(
        UUID organizationId
    ) {

        long lockKey =
            organizationId
                .getMostSignificantBits()
            ^ organizationId
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

    private record AlertKey(
        StockAlertType alertType,
        UUID stockItemId,
        UUID stockLocationId,
        UUID lotId
    ) {
    }

    private record AlertDefinition(
        StockItem stockItem,
        StockLocation stockLocation,
        StockLot lot,
        StockAlertType alertType,
        StockAlertSeverity severity,
        BigDecimal thresholdValue,
        BigDecimal observedValue
    ) {
    }
}