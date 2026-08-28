package com.sup2i.food.subscription.service;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.common.domain.MealType;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.organization.domain.Organization;
import com.sup2i.food.subscription.api.dto.CreatePlanRequest;
import com.sup2i.food.subscription.api.dto.SubscriptionPlanResponse;
import com.sup2i.food.subscription.domain.QuotaPeriodType;
import com.sup2i.food.subscription.domain.RenewalPolicy;
import com.sup2i.food.subscription.domain.SubscriptionPlan;
import com.sup2i.food.subscription.domain.SubscriptionPlanVersion;
import com.sup2i.food.subscription.domain.SubscriptionPlanVersionService;
import com.sup2i.food.subscription.domain.SuspensionPolicy;
import com.sup2i.food.subscription.exception.SubscriptionConflictException;
import com.sup2i.food.subscription.exception.SubscriptionNotFoundException;
import com.sup2i.food.subscription.repository.SubscriptionPlanRepository;
import com.sup2i.food.subscription.repository.SubscriptionPlanVersionRepository;
import com.sup2i.food.subscription.repository.SubscriptionPlanVersionServiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SubscriptionPlanService {

    private static final int
        MAX_PAGE_SIZE =
            100;

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionPlanVersionRepository versionRepository;
    private final SubscriptionPlanVersionServiceRepository versionServiceRepository;
    private final UserRepository userRepository;

    public SubscriptionPlanService(
        SubscriptionPlanRepository planRepository,
        SubscriptionPlanVersionRepository versionRepository,
        SubscriptionPlanVersionServiceRepository versionServiceRepository,
        UserRepository userRepository
    ) {
        this.planRepository =
            planRepository;

        this.versionRepository =
            versionRepository;

        this.versionServiceRepository =
            versionServiceRepository;

        this.userRepository =
            userRepository;
    }

    @Transactional
    public SubscriptionPlanResponse createPlan(
        UUID actorId,
        CreatePlanRequest request
    ) {

        User actor =
            resolveActor(actorId);

        Organization organization =
            actor.getOrganization();

        if (
            planRepository
                .existsByOrganization_IdAndCode(
                    organization.getId(),
                    request.code()
                )
        ) {
            throw new SubscriptionConflictException(
                "A plan with this code already exists."
            );
        }

        SubscriptionPlan plan =
            new SubscriptionPlan(
                organization,
                request.name(),
                request.code(),
                request.billingPeriod(),
                request.price()
            );

        plan.setIncludedMeals(
            request.includedMeals()
        );

        plan.setValidityDays(
            request.validityDays()
        );

        plan.setQuotaType(
            request.quotaType()
        );

        plan.setQuotaValue(
            request.quotaValue()
        );

        plan.setMaxPerDay(
            request.maxPerDay()
        );

        plan.setAllowedDays(
            request.allowedDays()
        );

        plan.setReservationRequired(
            request.reservationRequired()
        );

        QuotaPeriodType quotaPeriodType =
            request.quotaPeriodType()
                == null
                    ? QuotaPeriodType.SUBSCRIPTION
                    : request.quotaPeriodType();

        plan.setQuotaPeriodType(
            quotaPeriodType
        );

        RenewalPolicy renewalPolicy =
            request.renewalPolicy()
                == null
                    ? RenewalPolicy.MANUAL
                    : request.renewalPolicy();

        plan.setRenewalPolicy(
            renewalPolicy
        );

        SuspensionPolicy suspensionPolicy =
            request.suspensionPolicy()
                == null
                    ? SuspensionPolicy.BLOCK_USAGE
                    : request.suspensionPolicy();

        plan.setSuspensionPolicy(
            suspensionPolicy
        );

        if (
            request.audienceType()
                != null
        ) {
            plan.setAudienceType(
                request.audienceType()
            );
        }

        try {

            plan =
                planRepository
                    .saveAndFlush(plan);

        } catch (
            org.springframework.dao.DataIntegrityViolationException exception
        ) {
            throw new SubscriptionConflictException(
                "A plan with this code already exists."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        SubscriptionPlanVersion version =
            new SubscriptionPlanVersion(
                plan,
                1,
                plan.getAudienceType(),
                plan.getBillingPeriod(),
                plan.getPrice(),
                quotaPeriodType,
                renewalPolicy,
                suspensionPolicy,
                now
            );

        version.setIncludedMeals(
            request.includedMeals()
        );

        version.setValidityDays(
            request.validityDays()
        );

        version.setQuotaType(
            request.quotaType()
        );

        version.setQuotaValue(
            request.quotaValue()
        );

        version.setMaxPerDay(
            request.maxPerDay()
        );

        version.setAllowedDays(
            request.allowedDays()
        );

        version.setReservationRequired(
            request.reservationRequired()
        );

        version.setCreatedBy(
            actor
        );

        version =
            versionRepository
                .saveAndFlush(version);

        for (
            MealType service
            : request.services()
        ) {

            versionServiceRepository
                .save(
                    new SubscriptionPlanVersionService(
                        version,
                        service
                    )
                );
        }

        versionServiceRepository.flush();

        return response(
            plan,
            version
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<SubscriptionPlanResponse>
        listActivePlans(
            UUID actorId,
            int page,
            int size
        ) {

        User actor =
            resolveActor(actorId);

        int safePage =
            Math.max(page, 0);

        int safeSize =
            Math.min(
                Math.max(size, 1),
                MAX_PAGE_SIZE
            );

        Page<SubscriptionPlan> plans =
            planRepository
                .findAllByOrganization_IdAndActiveTrue(
                    actor.getOrganization()
                        .getId(),
                    PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(
                            Sort.Order.asc("name"),
                            Sort.Order.asc("id")
                        )
                    )
                );

        return PageResponse.from(
            plans.map(plan ->
                response(
                    plan,
                    currentVersion(
                        plan.getId()
                    )
                )
            )
        );
    }

    SubscriptionPlan ownedPlan(
        UUID planId,
        UUID organizationId
    ) {

        return planRepository
            .findByIdAndOrganization_Id(
                planId,
                organizationId
            )
            .orElseThrow(() ->
                new SubscriptionNotFoundException(
                    "Subscription plan does not exist."
                )
            );
    }

    SubscriptionPlanVersion currentVersion(
        UUID planId
    ) {

        return versionRepository
            .findByPlan_IdAndEffectiveToIsNull(
                planId
            )
            .orElseThrow(() ->
                new SubscriptionNotFoundException(
                    "Subscription plan has no current version."
                )
            );
    }

    Set<MealType> servicesFor(
        UUID planVersionId
    ) {

        return versionServiceRepository
            .findAllByPlanVersion_Id(
                planVersionId
            )
            .stream()
            .map(
                SubscriptionPlanVersionService::getServiceType
            )
            .collect(
                Collectors.toCollection(
                    java.util.LinkedHashSet::new
                )
            );
    }

    private User resolveActor(
        UUID actorId
    ) {

        return userRepository
            .findById(actorId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user does not exist."
                )
            );
    }

    private SubscriptionPlanResponse response(
        SubscriptionPlan plan,
        SubscriptionPlanVersion version
    ) {

        return new SubscriptionPlanResponse(
            plan.getId(),
            plan.getName(),
            plan.getCode(),
            plan.getBillingPeriod(),
            plan.getPrice(),
            plan.getIncludedMeals(),
            plan.getValidityDays(),
            plan.getQuotaValue(),
            plan.getMaxPerDay(),
            plan.isReservationRequired(),
            plan.isActive(),
            plan.getAudienceType(),
            servicesFor(
                version.getId()
            ),
            version.getId(),
            version.getVersionNumber()
        );
    }
}
