package com.sup2i.food.subscription.domain;

import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.common.domain.MealType;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "meal_usages")
public class MealUsage {

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
        name = "entitlement_id",
        nullable = false
    )
    private MealEntitlement entitlement;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "student_id",
        nullable = false
    )
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "meal_type",
        nullable = false,
        length = 30
    )
    private MealType mealType;

    @Column(
        name = "usage_date",
        nullable = false
    )
    private LocalDate usageDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_pass_id")
    private FoodPass foodPass;

    @Column(
        name = "consumed_at",
        nullable = false
    )
    private OffsetDateTime consumedAt;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "validated_by",
        nullable = false
    )
    private User validatedBy;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 20
    )
    private MealUsageStatus status =
        MealUsageStatus.VALID;

    protected MealUsage() {
    }

    public MealUsage(
        MealEntitlement entitlement,
        Student student,
        MealType mealType,
        LocalDate usageDate,
        FoodPass foodPass,
        User validatedBy
    ) {
        this.entitlement =
            entitlement;

        this.student =
            student;

        this.mealType =
            mealType;

        this.usageDate =
            usageDate;

        this.foodPass =
            foodPass;

        this.consumedAt =
            OffsetDateTime.now();

        this.validatedBy =
            validatedBy;
    }

    public UUID getId() {
        return id;
    }

    public MealEntitlement getEntitlement() {
        return entitlement;
    }

    public Student getStudent() {
        return student;
    }

    public MealType getMealType() {
        return mealType;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public FoodPass getFoodPass() {
        return foodPass;
    }

    public OffsetDateTime getConsumedAt() {
        return consumedAt;
    }

    public User getValidatedBy() {
        return validatedBy;
    }

    public MealUsageStatus getStatus() {
        return status;
    }
}