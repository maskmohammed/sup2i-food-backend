package com.sup2i.food.notification;

import com.sup2i.food.kitchen.service.KitchenReadyService;
import com.sup2i.food.notification.api.dto.CreateNotificationCommand;
import com.sup2i.food.notification.api.dto.NotificationPreferenceResponse;
import com.sup2i.food.notification.api.dto.NotificationResponse;
import com.sup2i.food.notification.api.dto.UpsertNotificationPreferenceCommand;
import com.sup2i.food.notification.domain.NotificationCategory;
import com.sup2i.food.notification.domain.NotificationChannel;
import com.sup2i.food.notification.domain.NotificationEventDefinition;
import com.sup2i.food.notification.domain.NotificationEventType;
import com.sup2i.food.notification.domain.NotificationPriority;
import com.sup2i.food.notification.domain.NotificationStatus;
import com.sup2i.food.notification.exception.NotificationNotFoundException;
import com.sup2i.food.notification.service.NotificationAfterCommitService;
import com.sup2i.food.notification.service.NotificationDeliveryService;
import com.sup2i.food.notification.service.NotificationEventCatalog;
import com.sup2i.food.notification.service.NotificationPreferenceService;
import com.sup2i.food.notification.service.NotificationService;
import com.sup2i.food.notification.service.OrderNotificationDispatchService;
import com.sup2i.food.payment.service.PaymentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@ActiveProfiles("test")
@Testcontainers
class NotificationE2EIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName(
                "sup2i_food_test"
            );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationDeliveryService notificationDeliveryService;

    @Autowired
    private NotificationPreferenceService notificationPreferenceService;

    @Autowired
    private NotificationAfterCommitService notificationAfterCommitService;

    @Autowired
    private NotificationEventCatalog notificationEventCatalog;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private KitchenReadyService kitchenReadyService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID organizationId;

    private UUID userId;

    @BeforeEach
    void seedTenant() {

        String suffix =
            UUID.randomUUID()
                .toString()
                .replace(
                    "-",
                    ""
                )
                .substring(
                    0,
                    10
                );

        organizationId =
            UUID.randomUUID();

        userId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO organizations (
                id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, TRUE)
            """,
            organizationId,
            "Notification " + suffix,
            "N" + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO users (
                id,
                organization_id,
                email,
                first_name,
                last_name,
                status
            )
            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """,
            userId,
            organizationId,
            "notification-" + suffix + "@sup2i.test",
            "Notification",
            "E2E"
        );
    }

    @Test
    void afterCommitPersistsOnlyAfterBusinessCommit() {

        UUID notificationId =
            UUID.randomUUID();

        CreateNotificationCommand command =
            command(
                "ORDER_READY",
                NotificationChannel.IN_APP,
                "after-commit"
            );

        TransactionTemplate transaction =
            transaction();

        transaction.executeWithoutResult(
            status -> {

                notificationAfterCommitService
                    .enqueueAfterCommit(
                        organizationId,
                        notificationId,
                        userId,
                        command
                    );

                assertThat(
                    countNotification(
                        notificationId
                    )
                )
                    .isZero();
            }
        );

        assertThat(
            countNotification(
                notificationId
            )
        )
            .isEqualTo(
                1
            );

        NotificationResponse stored =
            notificationService.get(
                organizationId,
                notificationId
            );

        assertThat(stored.status())
            .isEqualTo(
                NotificationStatus.PENDING
            );

        assertThat(stored.replayed())
            .isFalse();
    }

    @Test
    void businessRollbackCreatesNoGhostNotification() {

        UUID notificationId =
            UUID.randomUUID();

        CreateNotificationCommand command =
            command(
                "PAYMENT_CONFIRMED",
                NotificationChannel.PUSH,
                "rollback"
            );

        TransactionTemplate transaction =
            transaction();

        transaction.executeWithoutResult(
            status -> {

                notificationAfterCommitService
                    .enqueueAfterCommit(
                        organizationId,
                        notificationId,
                        userId,
                        command
                    );

                status.setRollbackOnly();
            }
        );

        assertThat(
            countNotification(
                notificationId
            )
        )
            .isZero();
    }

    @Test
    void activeDeduplicationReplaysSingleStoredNotification() {

        UUID firstId =
            UUID.randomUUID();

        UUID secondId =
            UUID.randomUUID();

        String deduplicationKey =
            "ORDER_READY:"
                + UUID.randomUUID();

        CreateNotificationCommand command =
            new CreateNotificationCommand(
                "ORDER_READY",
                NotificationChannel.IN_APP,
                "Commande prête",
                "Votre commande TEST est prête.",
                null,
                NotificationPriority.HIGH,
                deduplicationKey,
                null
            );

        NotificationResponse first =
            notificationService.enqueue(
                organizationId,
                firstId,
                userId,
                command
            );

        NotificationResponse replay =
            notificationService.enqueue(
                organizationId,
                secondId,
                userId,
                command
            );

        assertThat(first.replayed())
            .isFalse();

        assertThat(replay.replayed())
            .isTrue();

        assertThat(replay.id())
            .isEqualTo(
                first.id()
            );

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM notifications
                WHERE user_id = ?
                  AND channel = 'IN_APP'
                  AND deduplication_key = ?
                """,
                Integer.class,
                userId,
                deduplicationKey
            );

        assertThat(count)
            .isEqualTo(
                1
            );
    }

    @Test
    void deliveryLifecycleSupportsFailureRetrySentAndRead() {

        UUID notificationId =
            UUID.randomUUID();

        NotificationResponse pending =
            notificationService.enqueue(
                organizationId,
                notificationId,
                userId,
                command(
                    "ORDER_READY",
                    NotificationChannel.IN_APP,
                    "delivery"
                )
            );

        assertThat(pending.status())
            .isEqualTo(
                NotificationStatus.PENDING
            );

        NotificationResponse failed =
            notificationDeliveryService.markFailed(
                organizationId,
                notificationId,
                "temporary failure"
            );

        assertThat(failed.status())
            .isEqualTo(
                NotificationStatus.FAILED
            );

        assertThat(failed.retryCount())
            .isEqualTo(
                1
            );

        assertThat(failed.lastError())
            .isEqualTo(
                "temporary failure"
            );

        NotificationResponse failureReplay =
            notificationDeliveryService.markFailed(
                organizationId,
                notificationId,
                "temporary failure"
            );

        assertThat(failureReplay.replayed())
            .isTrue();

        assertThat(failureReplay.retryCount())
            .isEqualTo(
                1
            );

        NotificationResponse retry =
            notificationDeliveryService.retry(
                organizationId,
                notificationId,
                null
            );

        assertThat(retry.status())
            .isEqualTo(
                NotificationStatus.PENDING
            );

        assertThat(retry.retryCount())
            .isEqualTo(
                1
            );

        assertThat(retry.lastError())
            .isNull();

        NotificationResponse sent =
            notificationDeliveryService.markSent(
                organizationId,
                notificationId
            );

        assertThat(sent.status())
            .isEqualTo(
                NotificationStatus.SENT
            );

        assertThat(sent.sentAt())
            .isNotNull();

        NotificationResponse read =
            notificationService.markRead(
                organizationId,
                userId,
                notificationId
            );

        assertThat(read.status())
            .isEqualTo(
                NotificationStatus.READ
            );

        assertThat(read.readAt())
            .isNotNull();
    }

    @Test
    void preferencesExposeDefaultsAndPersistCategoryChoice() {

        NotificationPreferenceResponse defaults =
            notificationPreferenceService
                .getOrDefault(
                    organizationId,
                    userId,
                    NotificationCategory.CANTEEN
                );

        assertThat(defaults.persisted())
            .isFalse();

        assertThat(defaults.pushEnabled())
            .isTrue();

        assertThat(defaults.emailEnabled())
            .isTrue();

        assertThat(defaults.inAppEnabled())
            .isTrue();

        NotificationPreferenceResponse stored =
            notificationPreferenceService.upsert(
                organizationId,
                userId,
                NotificationCategory.CANTEEN,
                new UpsertNotificationPreferenceCommand(
                    false,
                    true,
                    false,
                    LocalTime.of(
                        22,
                        0
                    ),
                    LocalTime.of(
                        7,
                        0
                    )
                )
            );

        assertThat(stored.persisted())
            .isTrue();

        assertThat(stored.pushEnabled())
            .isFalse();

        assertThat(stored.emailEnabled())
            .isTrue();

        assertThat(stored.inAppEnabled())
            .isFalse();

        assertThat(stored.quietHoursStart())
            .isEqualTo(
                LocalTime.of(
                    22,
                    0
                )
            );

        assertThat(stored.quietHoursEnd())
            .isEqualTo(
                LocalTime.of(
                    7,
                    0
                )
            );

        assertThat(
            notificationPreferenceService.isChannelEnabled(
                organizationId,
                userId,
                NotificationCategory.CANTEEN,
                NotificationChannel.PUSH
            )
        )
            .isFalse();
    }

    @Test
    void notificationReadsAreTenantIsolated() {

        UUID notificationId =
            UUID.randomUUID();

        notificationService.enqueue(
            organizationId,
            notificationId,
            userId,
            command(
                "ORDER_READY",
                NotificationChannel.IN_APP,
                "tenant"
            )
        );

        UUID otherOrganizationId =
            UUID.randomUUID();

        String otherCode =
            "O"
                + UUID.randomUUID()
                    .toString()
                    .replace(
                        "-",
                        ""
                    )
                    .substring(
                        0,
                        10
                    );

        jdbcTemplate.update(
            """
            INSERT INTO organizations (
                id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, TRUE)
            """,
            otherOrganizationId,
            "Other Notification Tenant",
            otherCode
        );

        assertThatThrownBy(
            () ->
                notificationService.get(
                    otherOrganizationId,
                    notificationId
                )
        )
            .isInstanceOf(
                NotificationNotFoundException.class
            );
    }

    @Test
    void catalogAndCanonicalServicesUseB18ProductionWiring() {

        assertThat(
            notificationEventCatalog.all()
        )
            .hasSize(
                15
            );

        NotificationEventDefinition payment =
            notificationEventCatalog.get(
                NotificationEventType.PAYMENT_CONFIRMED
            );

        assertThat(payment.priority())
            .isEqualTo(
                NotificationPriority.HIGH
            );

        assertThat(payment.requiredChannels())
            .containsExactlyInAnyOrder(
                NotificationChannel.PUSH,
                NotificationChannel.IN_APP
            );

        NotificationEventDefinition ready =
            notificationEventCatalog.get(
                NotificationEventType.ORDER_READY
            );

        assertThat(ready.priority())
            .isEqualTo(
                NotificationPriority.HIGH
            );

        assertThat(ready.requiredChannels())
            .containsExactlyInAnyOrder(
                NotificationChannel.PUSH,
                NotificationChannel.IN_APP
            );

        Object paymentDispatcher =
            ReflectionTestUtils.getField(
                paymentService,
                "notificationDispatchService"
            );

        Object readyDispatcher =
            ReflectionTestUtils.getField(
                kitchenReadyService,
                "notificationDispatchService"
            );

        assertThat(paymentDispatcher)
            .isInstanceOf(
                OrderNotificationDispatchService.class
            );

        assertThat(readyDispatcher)
            .isInstanceOf(
                OrderNotificationDispatchService.class
            );
    }

    private TransactionTemplate transaction() {

        return new TransactionTemplate(
            transactionManager
        );
    }

    private CreateNotificationCommand command(
        String type,
        NotificationChannel channel,
        String suffix
    ) {

        String deduplicationKey =
            type
                + ":"
                + channel.name()
                + ":"
                + UUID.randomUUID();

        return new CreateNotificationCommand(
            type,
            channel,
            "Notification " + suffix,
            "Notification body " + suffix,
            null,
            NotificationPriority.NORMAL,
            deduplicationKey,
            null
        );
    }

    private int countNotification(
        UUID notificationId
    ) {

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM notifications
                WHERE id = ?
                """,
                Integer.class,
                notificationId
            );

        if (count == null) {
            return 0;
        }

        return count;
    }
}