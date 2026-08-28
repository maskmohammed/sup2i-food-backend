package com.sup2i.food.promotion.domain;

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

import java.util.UUID;

@Entity
@Table(name = "promotion_targets")
public class PromotionTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private TargetType targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "include_target", nullable = false)
    private boolean includeTarget = true;

    protected PromotionTarget() {
    }

    public PromotionTarget(
        Promotion promotion,
        TargetType targetType,
        UUID targetId
    ) {
        this.promotion = promotion;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public UUID getId() {
        return id;
    }

    public Promotion getPromotion() {
        return promotion;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public boolean isIncludeTarget() {
        return includeTarget;
    }
}