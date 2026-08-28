package com.sup2i.food.notification.domain;

import com.sup2i.food.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(
        strategy = GenerationType.UUID
    )
    private UUID id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "user_id",
        nullable = false
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "type",
        nullable = false,
        length = 50
    )
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "channel",
        nullable = false,
        length = 30
    )
    private NotificationChannel channel;

    @Column(
        name = "title",
        nullable = false,
        length = 180
    )
    private String title;

    @Column(
        name = "body",
        nullable = false,
        columnDefinition = "text"
    )
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
        name = "payload",
        columnDefinition = "jsonb"
    )
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    private NotificationStatus status;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "priority",
        nullable = false,
        length = 20
    )
    private NotificationPriority priority;

    @Column(
        name = "deduplication_key",
        length = 180
    )
    private String deduplicationKey;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(
        name = "retry_count",
        nullable = false
    )
    private int retryCount;

    @Column(
        name = "last_error",
        columnDefinition = "text"
    )
    private String lastError;

    protected Notification() {
    }

    public Notification(
        User user,
        NotificationType type,
        NotificationChannel channel,
        String title,
        String body,
        String payload
    ) {
        this.user =
            user;

        this.type =
            type;

        this.channel =
            channel;

        this.title =
            title;

        this.body =
            body;

        this.payload =
            payload;

        this.status =
            NotificationStatus.PENDING;

        this.priority =
            NotificationPriority.NORMAL;

        this.retryCount =
            0;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getPayload() {
        return payload;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public String getDeduplicationKey() {
        return deduplicationKey;
    }

    public OffsetDateTime getScheduledAt() {
        return scheduledAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void markRead(
        OffsetDateTime readAt
    ) {
        this.status =
            NotificationStatus.READ;

        this.readAt =
            readAt;
    }
}
