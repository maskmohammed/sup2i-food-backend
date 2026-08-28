package com.sup2i.food.subscription.domain;

import com.sup2i.food.common.domain.MealType;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscription_plan_version_services")
public class SubscriptionPlanVersionService {

    @EmbeddedId
    private SubscriptionPlanVersionServiceId id;

    @MapsId("planVersionId")
    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "plan_version_id",
        nullable = false
    )
    private SubscriptionPlanVersion planVersion;

    protected SubscriptionPlanVersionService() {
    }

    public SubscriptionPlanVersionService(
        SubscriptionPlanVersion planVersion,
        MealType serviceType
    ) {
        this.planVersion =
            planVersion;

        this.id =
            new SubscriptionPlanVersionServiceId(
                planVersion.getId(),
                serviceType
            );
    }

    public SubscriptionPlanVersionServiceId getId() {
        return id;
    }

    public SubscriptionPlanVersion getPlanVersion() {
        return planVersion;
    }

    public MealType getServiceType() {
        return id.getServiceType();
    }
}
