package com.sup2i.food.identity.domain;

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

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_login_events")
public class AuthLoginEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "identifier_hash", length = 128)
    private String identifierHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 30)
    private AuthLoginResult result;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private InetAddress ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    protected AuthLoginEvent() {
    }

    public AuthLoginEvent(AuthLoginResult result) {
        this.result = result;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getIdentifierHash() {
        return identifierHash;
    }

    public void setIdentifierHash(String identifierHash) {
        this.identifierHash = identifierHash;
    }

    public AuthLoginResult getResult() {
        return result;
    }

    public void setResult(AuthLoginResult result) {
        this.result = result;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(InetAddress ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}