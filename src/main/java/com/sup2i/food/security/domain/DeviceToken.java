package com.sup2i.food.security.domain;

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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "device_tokens")
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "token", nullable = false, unique = true, length = 512)
    private String token;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected DeviceToken() {
    }

    public DeviceToken(
        User user,
        DevicePlatform platform,
        String token
    ) {
        this.user = user;
        this.platform = platform;
        this.token = token;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public String getToken() {
        return token;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void markSeen(OffsetDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate(OffsetDateTime lastSeenAt) {
        this.active = true;
        this.lastSeenAt = lastSeenAt;
    }
}