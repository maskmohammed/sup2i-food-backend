package com.sup2i.food.subscription.service;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.common.domain.MealType;
import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.StudentRepository;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.subscription.api.dto.ActivateSubscriptionRequest;
import com.sup2i.food.subscription.api.dto.MealEntitlementResponse;
import com.sup2i.food.subscription.api.dto.SubscribeRequest;
import com.sup2i.food.subscription.api.dto.SubscriptionMutationResponse;
import com.sup2i.food.subscription.api.dto.SubscriptionResponse;
import com.sup2i.food.subscription.api.dto.SubscriptionStatusHistoryResponse;
import com.sup2i.food.subscription.domain.BillingPeriod;
import com.sup2i.food.subscription.domain.MealEntitlement;
import com.sup2i.food.subscription.domain.Subscription;
import com.sup2i.food.subscription.domain.SubscriptionPlan;
import com.sup2i.food.subscription.domain.SubscriptionPlanVersion;
import com.sup2i.food.subscription.domain.SubscriptionStatus;
import com.sup2i.food.subscription.domain.SubscriptionStatusHistory;
import com.sup2i.food.subscription.exception.SubscriptionConflictException;
import com.sup2i.food.subscription.exception.SubscriptionNotFoundException;
import com.sup2i.food.subscription.exception.SubscriptionValidationException;
import com.sup2i.food.subscription.repository.MealEntitlementRepository;
import com.sup2i.food.subscription.repository.SubscriptionRepository;
import com.sup2i.food.subscription.repository.SubscriptionStatusHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SubscriptionService {

    private static final int
        MAX_PAGE_SIZE =
            100;

    private static final int
        DAYS_WEEK =
            7;

    private static final int
        DAYS_MONTH =
            30;

    private static final int
        DAYS_SEMESTER =
            180;

    private static final int
        DAYS_SCHOOL_YEAR =
            365;

    private final SubscriptionRepository subscriptionRepository;
    private final MealEntitlementRepository entitlementRepository;
    private final SubscriptionStatusHistoryRepository historyRepository;
    private final SubscriptionPlanService planService;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public SubscriptionService(
        SubscriptionRepository subscriptionRepository,
        MealEntitlementRepository entitlementRepository,
        SubscriptionStatusHistoryRepository historyRepository,
        SubscriptionPlanService planService,
        StudentRepository studentRepository,
        UserRepository userRepository
    ) {
        this.subscriptionRepository =
            subscriptionRepository;

        this.entitlementRepository =
            entitlementRepository;

        this.historyRepository =
            historyRepository;

        this.planService =
            planService;

        this.studentRepository =
            studentRepository;

        this.userRepository =
            userRepository;
    }

    // =========================================================
    // STUDENT SELF-SERVICE
    // =========================================================

    @Transactional
    public SubscriptionMutationResponse subscribe(
        UUID studentUserId,
        SubscribeRequest request
    ) {

        Student student =
            student(studentUserId);

        SubscriptionPlan plan =
            planService.ownedPlan(
                request.planId(),
                student.getUser()
                    .getOrganization()
                    .getId()
            );

        if (
            !plan.isActive()
        ) {
            throw new SubscriptionValidationException(
                "Subscription plan is not active."
            );
        }

        if (
            plan.getAudienceType()
                != com.sup2i.food.subscription.domain.AudienceType.STUDENT
            && plan.getAudienceType()
                != com.sup2i.food.subscription.domain.AudienceType.ANY
        ) {
            throw new SubscriptionValidationException(
                "This subscription plan is not available for students."
            );
        }

        SubscriptionPlanVersion version =
            planService.currentVersion(
                plan.getId()
            );

        OffsetDateTime now =
            OffsetDateTime.now();

        if (
            version.getSaleStartsAt()
                != null
            && now.isBefore(
                version.getSaleStartsAt()
            )
        ) {
            throw new SubscriptionConflictException(
                "Plan sales have not started yet."
            );
        }

        if (
            version.getSaleEndsAt()
                != null
            && now.isAfter(
                version.getSaleEndsAt()
            )
        ) {
            throw new SubscriptionConflictException(
                "Plan sales have ended."
            );
        }

        Set<MealType> services =
            planService.servicesFor(
                version.getId()
            );

        ensureNoOverlap(
            student.getId(),
            services
        );

        LocalDate startsAt =
            LocalDate.now();

        LocalDate endsAt =
            endsAt(
                startsAt,
                version
            );

        Subscription subscription =
            new Subscription(
                student,
                plan,
                version,
                startsAt,
                endsAt
            );

        subscription =
            subscriptionRepository
                .saveAndFlush(subscription);

        for (
            MealType service
            : services
        ) {
            entitlementRepository.save(
                entitlement(
                    subscription,
                    service,
                    version
                )
            );
        }

        entitlementRepository.flush();

        historyRepository.save(
            new SubscriptionStatusHistory(
                subscription,
                null,
                SubscriptionStatus.PENDING,
                student.getUser(),
                "Subscribed to plan "
                    + plan.getName()
            )
        );

        return new SubscriptionMutationResponse(
            response(subscription),
            false
        );
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listOwned(
        UUID studentUserId
    ) {

        Student student =
            student(studentUserId);

        return subscriptionRepository
            .findAllByStudent_IdOrderByCreatedAtDesc(
                student.getId()
            )
            .stream()
            .map(this::response)
            .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse findOwned(
        UUID studentUserId,
        UUID subscriptionId
    ) {

        Student student =
            student(studentUserId);

        return response(
            subscriptionRepository
                .findOwnedByIdForUpdate(
                    subscriptionId,
                    student.getId()
                )
                .orElseThrow(() ->
                    new SubscriptionNotFoundException(
                        "Subscription does not exist."
                    )
                )
        );
    }

    @Transactional
    public SubscriptionMutationResponse cancelOwn(
        UUID studentUserId,
        UUID subscriptionId,
        String reason
    ) {

        Student student =
            student(studentUserId);

        Subscription subscription =
            subscriptionRepository
                .findOwnedByIdForUpdate(
                    subscriptionId,
                    student.getId()
                )
                .orElseThrow(() ->
                    new SubscriptionNotFoundException(
                        "Subscription does not exist."
                    )
                );

        if (
            subscription.getStatus()
                != SubscriptionStatus.PENDING
        ) {
            throw new SubscriptionConflictException(
                "Only a pending subscription can be cancelled by the student."
            );
        }

        User actor =
            student.getUser();

        cancel(
            subscription,
            actor,
            reason
        );

        return new SubscriptionMutationResponse(
            response(subscription),
            false
        );
    }

    @Transactional(readOnly = true)
    public List<SubscriptionStatusHistoryResponse> history(
        UUID studentUserId,
        UUID subscriptionId
    ) {

        Student student =
            student(studentUserId);

        subscriptionRepository
            .findOwnedByIdForUpdate(
                subscriptionId,
                student.getId()
            )
            .orElseThrow(() ->
                new SubscriptionNotFoundException(
                    "Subscription does not exist."
                )
            );

        return historyRepository
            .findAllBySubscription_IdOrderByCreatedAtAsc(
                subscriptionId
            )
            .stream()
            .map(this::historyResponse)
            .toList();
    }

    // =========================================================
    // ADMIN OPERATIONS
    // =========================================================

    @Transactional
    public SubscriptionMutationResponse activate(
        UUID actorId,
        UUID subscriptionId,
        ActivateSubscriptionRequest request
    ) {

        User actor =
            operator(actorId);

        Subscription subscription =
            subscriptionRepository
                .findByIdAndOrganizationForUpdate(
                    subscriptionId,
                    actor.getOrganization()
                        .getId()
                )
                .orElseThrow(() ->
                    new SubscriptionNotFoundException(
                        "Subscription does not exist."
                    )
                );

        if (
            subscription.getStatus()
                != SubscriptionStatus.PENDING
        ) {
            throw new SubscriptionConflictException(
                "Only a pending subscription can be activated."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        subscription.activate(
            actor,
            now,
            request.paymentReference(),
            request.administrativePaymentAmount()
        );

        historyRepository.save(
            new SubscriptionStatusHistory(
                subscription,
                SubscriptionStatus.PENDING,
                SubscriptionStatus.ACTIVE,
                actor,
                "Activated with payment reference "
                    + request.paymentReference()
            )
        );

        subscriptionRepository
            .saveAndFlush(subscription);

        return new SubscriptionMutationResponse(
            response(subscription),
            false
        );
    }

    @Transactional
    public SubscriptionMutationResponse suspend(
        UUID actorId,
        UUID subscriptionId,
        String reason
    ) {

        User actor =
            operator(actorId);

        Subscription subscription =
            owned(actor, subscriptionId);

        if (
            subscription.getStatus()
                != SubscriptionStatus.ACTIVE
        ) {
            throw new SubscriptionConflictException(
                "Only an active subscription can be suspended."
            );
        }

        SubscriptionStatus from =
            subscription.getStatus();

        subscription.suspend(
            OffsetDateTime.now()
        );

        recordTransition(
            subscription,
            from,
            SubscriptionStatus.SUSPENDED,
            actor,
            reason
        );

        return new SubscriptionMutationResponse(
            response(subscription),
            false
        );
    }

    @Transactional
    public SubscriptionMutationResponse reactivate(
        UUID actorId,
        UUID subscriptionId,
        String reason
    ) {

        User actor =
            operator(actorId);

        Subscription subscription =
            owned(actor, subscriptionId);

        if (
            subscription.getStatus()
                != SubscriptionStatus.SUSPENDED
        ) {
            throw new SubscriptionConflictException(
                "Only a suspended subscription can be reactivated."
            );
        }

        SubscriptionStatus from =
            subscription.getStatus();

        subscription.reactivate();

        recordTransition(
            subscription,
            from,
            SubscriptionStatus.ACTIVE,
            actor,
            reason
        );

        return new SubscriptionMutationResponse(
            response(subscription),
            false
        );
    }

    @Transactional
    public SubscriptionMutationResponse cancel(
        UUID actorId,
        UUID subscriptionId,
        String reason
    ) {

        User actor =
            operator(actorId);

        Subscription cancelled =
            cancel(
                owned(actor, subscriptionId),
                actor,
                reason
            );

        return new SubscriptionMutationResponse(
            response(cancelled),
            false
        );
    }

    @Transactional
    public int expireOutdated() {

        List<Subscription> outdated =
            subscriptionRepository
                .findAllByStatusAndEndsAtBefore(
                    SubscriptionStatus.ACTIVE,
                    LocalDate.now()
                );

        for (
            Subscription subscription
            : outdated
        ) {
            SubscriptionStatus from =
                subscription.getStatus();

            subscription.expire();

            recordTransition(
                subscription,
                from,
                SubscriptionStatus.EXPIRED,
                null,
                "Subscription validity ended."
            );
        }

        if (
            !outdated.isEmpty()
        ) {
            subscriptionRepository.saveAll(
                outdated
            );
        }

        return outdated.size();
    }

    @Transactional(readOnly = true)
    public PageResponse<SubscriptionResponse> listByOrganization(
        UUID actorId,
        int page,
        int size
    ) {

        User actor =
            operator(actorId);

        int safePage =
            Math.max(page, 0);

        int safeSize =
            Math.min(
                Math.max(size, 1),
                MAX_PAGE_SIZE
            );

        Page<Subscription> subscriptions =
            subscriptionRepository
                .findAllByPlan_Organization_Id(
                    actor.getOrganization()
                        .getId(),
                    PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(
                            Sort.Order.desc("createdAt"),
                            Sort.Order.desc("id")
                        )
                    )
                );

        return PageResponse.from(
            subscriptions.map(
                this::response
            )
        );
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse findByOrganization(
        UUID actorId,
        UUID subscriptionId
    ) {

        User actor =
            operator(actorId);

        return response(
            subscriptionRepository
                .findByIdAndPlan_Organization_Id(
                    subscriptionId,
                    actor.getOrganization()
                        .getId()
                )
                .orElseThrow(() ->
                    new SubscriptionNotFoundException(
                        "Subscription does not exist."
                    )
                )
        );
    }

    // =========================================================
    // INTERNALS
    // =========================================================

    @Scheduled(
        cron = "0 30 2 * * *"
    )
    protected void scheduledExpiry() {
        expireOutdated();
    }

    private Subscription cancel(
        Subscription subscription,
        User actor,
        String reason
    ) {

        if (
            subscription.getStatus()
                != SubscriptionStatus.PENDING
            && subscription.getStatus()
                != SubscriptionStatus.ACTIVE
            && subscription.getStatus()
                != SubscriptionStatus.SUSPENDED
        ) {
            throw new SubscriptionConflictException(
                "This subscription cannot be cancelled."
            );
        }

        SubscriptionStatus from =
            subscription.getStatus();

        subscription.cancel(
            OffsetDateTime.now()
        );

        recordTransition(
            subscription,
            from,
            SubscriptionStatus.CANCELLED,
            actor,
            reason
        );

        subscriptionRepository
            .saveAndFlush(subscription);

        return subscription;
    }

    private Subscription owned(
        User actor,
        UUID subscriptionId
    ) {

        return subscriptionRepository
            .findByIdAndOrganizationForUpdate(
                subscriptionId,
                actor.getOrganization()
                    .getId()
            )
            .orElseThrow(() ->
                new SubscriptionNotFoundException(
                    "Subscription does not exist."
                )
            );
    }

    private void ensureNoOverlap(
        UUID studentId,
        Set<MealType> requested
    ) {

        List<Subscription> activePending =
            subscriptionRepository
                .findAllByStudent_IdAndStatusIn(
                    studentId,
                    List.of(
                        SubscriptionStatus.PENDING,
                        SubscriptionStatus.ACTIVE,
                        SubscriptionStatus.SUSPENDED
                    )
                );

        for (
            Subscription subscription
            : activePending
        ) {

            Set<MealType> covered =
                entitlementRepository
                    .findAllBySubscription_IdOrderByMealTypeAsc(
                        subscription.getId()
                    )
                    .stream()
                    .map(
                        MealEntitlement::getMealType
                    )
                    .collect(
                        Collectors.toCollection(
                            LinkedHashSet::new
                        )
                    );

            Set<MealType> overlap =
                requested
                    .stream()
                    .filter(covered::contains)
                    .collect(
                        Collectors.toCollection(
                            LinkedHashSet::new
                        )
                    );

            if (
                !overlap.isEmpty()
            ) {
                throw new SubscriptionConflictException(
                    "An active or pending subscription already covers "
                        + overlap
                        + "."
                );
            }
        }
    }

    private MealEntitlement entitlement(
        Subscription subscription,
        MealType mealType,
        SubscriptionPlanVersion version
    ) {

        MealEntitlement entitlement =
            new MealEntitlement(
                subscription,
                mealType,
                subscription.getStartsAt(),
                subscription.getEndsAt(),
                version.getQuotaValue()
                    == null
                        ? version.getIncludedMeals()
                        : version.getQuotaValue(),
                version.getMaxPerDay()
                    == null
                        ? 1
                        : version.getMaxPerDay(),
                version.getQuotaPeriodType(),
                version.isReservationRequired()
            );

        entitlement.setAllowedDays(
            version.getAllowedDays()
        );

        return entitlement;
    }

    private LocalDate endsAt(
        LocalDate startsAt,
        SubscriptionPlanVersion version
    ) {

        Integer validityDays =
            version.getValidityDays();

        int days =
            validityDays == null
                ? daysFor(version.getBillingPeriod())
                : validityDays;

        if (
            days <= 0
        ) {
            throw new SubscriptionValidationException(
                "Plan validity cannot be determined."
            );
        }

        return startsAt.plusDays(
            days - 1L
        );
    }

    private int daysFor(
        BillingPeriod period
    ) {

        return switch (period) {
            case WEEK -> DAYS_WEEK;
            case MONTH -> DAYS_MONTH;
            case SEMESTER -> DAYS_SEMESTER;
            case SCHOOL_YEAR -> DAYS_SCHOOL_YEAR;
            case MEAL_PACK, CUSTOM -> 0;
        };
    }

    private void recordTransition(
        Subscription subscription,
        SubscriptionStatus from,
        SubscriptionStatus to,
        User changedBy,
        String reason
    ) {

        historyRepository.save(
            new SubscriptionStatusHistory(
                subscription,
                from,
                to,
                changedBy,
                reason
            )
        );
    }

    private Student student(
        UUID studentUserId
    ) {

        return studentRepository
            .findByUserId(studentUserId)
            .orElseThrow(() ->
                new SubscriptionValidationException(
                    "Authenticated user is not a registered student."
                )
            );
    }

    private User operator(
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

    private SubscriptionResponse response(
        Subscription subscription
    ) {

        List<MealEntitlementResponse> entitlements =
            entitlementRepository
                .findAllBySubscription_IdOrderByMealTypeAsc(
                    subscription.getId()
                )
                .stream()
                .map(this::entitlementResponse)
                .toList();

        return new SubscriptionResponse(
            subscription.getId(),
            subscription.getStudent()
                .getId(),
            subscription.getPlan()
                .getId(),
            subscription.getPlan()
                .getName(),
            subscription.getPlanVersion()
                .getId(),
            subscription.getStatus(),
            subscription.getStartsAt(),
            subscription.getEndsAt(),
            subscription.getPaymentReference(),
            subscription
                .getAdministrativePaymentAmount(),
            subscription.getActivatedAt(),
            subscription.getSuspendedAt(),
            subscription.getCancelledAt(),
            entitlements
        );
    }

    private MealEntitlementResponse entitlementResponse(
        MealEntitlement entitlement
    ) {

        return new MealEntitlementResponse(
            entitlement.getId(),
            entitlement.getMealType(),
            entitlement.getValidFrom(),
            entitlement.getValidTo(),
            entitlement.getTotalQuota(),
            entitlement.getDailyLimit()
        );
    }

    private SubscriptionStatusHistoryResponse
        historyResponse(
        SubscriptionStatusHistory history
    ) {

        return new SubscriptionStatusHistoryResponse(
            history.getId(),
            history.getFromStatus()
                == null
                    ? null
                    : history.getFromStatus()
                        .name(),
            history.getToStatus()
                .name(),
            history.getChangedBy()
                == null
                    ? null
                    : history.getChangedBy()
                        .getId(),
            history.getReason(),
            history.getCreatedAt()
        );
    }
}