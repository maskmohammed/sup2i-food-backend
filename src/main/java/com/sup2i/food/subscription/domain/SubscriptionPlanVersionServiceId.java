package com.sup2i.food.subscription.domain;

import com.sup2i.food.common.domain.MealType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SubscriptionPlanVersionServiceId
    implements Serializable {

    @Column(name = "plan_version_id")
    private UUID planVersionId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "service_type",
        length = 30
    )
    private MealType serviceType;

    protected SubscriptionPlanVersionServiceId() {
    }

    public SubscriptionPlanVersionServiceId(
        UUID planVersionId,
        MealType serviceType
    ) {
        this.planVersionId =
            planVersionId;

        this.serviceType =
            serviceType;
    }

    public UUID getPlanVersionId() {
        return planVersionId;
    }

    public MealType getServiceType() {
        return serviceType;
    }

    @Override
    public boolean equals(
        Object object
    ) {

        if (this == object) {
            return true;
        }

        if (
            object == null
            || getClass() != object.getClass()
        ) {
            return false;
        }

        SubscriptionPlanVersionServiceId that =
            (SubscriptionPlanVersionServiceId) object;

        return Objects.equals(
                planVersionId,
                that.planVersionId
            )
            && serviceType
                == that.serviceType;
    }

    @Override
    public int hashCode() {

        return Objects.hash(
            planVersionId,
            serviceType
        );
    }
}
