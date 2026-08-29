package com.sup2i.food.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(
        name = "organization_id",
        nullable = false
    )
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private com.sup2i.food.identity.domain.User user;

    @Column(name = "actor_role_code")
    private String actorRoleCode;

    @Column(
        nullable = false,
        length = 120
    )
    private String action;

    @Column(
        name = "resource_type",
        nullable = false,
        length = 80
    )
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data")
    private Map<String, Object> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data")
    private Map<String, Object> afterData;

    @Column(length = 1000)
    private String reason;

    @Column(
        nullable = false,
        length = 40
    )
    private String source;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private InetAddress ipAddress;

    @Column(
        nullable = false,
        length = 30
    )
    private String result;

    @Column(
        name = "created_at",
        nullable = false
    )
    private OffsetDateTime createdAt;

    public void record(
        UUID organizationId,
        com.sup2i.food.identity.domain.User user,
        String actorRoleCode,
        String action,
        String resourceType,
        UUID resourceId,
        Map<String, Object> beforeData,
        Map<String, Object> afterData,
        String reason,
        String source,
        InetAddress ipAddress,
        String result
    ) {
        this.organizationId =
            organizationId;

        this.user =
            user;

        this.actorRoleCode =
            actorRoleCode;

        this.action =
            action;

        this.resourceType =
            resourceType;

        this.resourceId =
            resourceId;

        this.beforeData =
            beforeData;

        this.afterData =
            afterData;

        this.reason =
            reason;

        this.source =
            source;

        this.ipAddress =
            ipAddress;

        this.result =
            result;

        this.createdAt =
            OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }
}