package com.sup2i.food.subscription.service;

import com.sup2i.food.common.domain.MealType;
import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.StudentRepository;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.subscription.api.dto.MealUsageResponse;
import com.sup2i.food.subscription.domain.MealEntitlement;
import com.sup2i.food.subscription.domain.MealUsage;
import com.sup2i.food.subscription.domain.MealUsageStatus;
import com.sup2i.food.subscription.domain.QuotaPeriodType;
import com.sup2i.food.subscription.domain.SubscriptionStatus;
import com.sup2i.food.subscription.exception.SubscriptionConflictException;
import com.sup2i.food.subscription.exception.SubscriptionValidationException;
import com.sup2i.food.subscription.repository.MealEntitlementRepository;
import com.sup2i.food.subscription.repository.MealUsageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class MealUsageService {

    private final MealUsageRepository mealUsageRepository;
    private final MealEntitlementRepository entitlementRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public MealUsageService(
        MealUsageRepository mealUsageRepository,
        MealEntitlementRepository entitlementRepository,
        StudentRepository studentRepository,
        UserRepository userRepository
    ) {
        this.mealUsageRepository =
            mealUsageRepository;

        this.entitlementRepository =
            entitlementRepository;

        this.studentRepository =
            studentRepository;

        this.userRepository =
            userRepository;
    }

    @Transactional
    public MealUsageResponse consume(
        UUID operatorUserId,
        UUID studentId,
        MealType mealType,
        LocalDate date
    ) {

        User operator =
            userRepository
                .findById(operatorUserId)
                .orElseThrow(() ->
                    new BadCredentialsException(
                        "Authenticated user does not exist."
                    )
                );

        Student student =
            studentRepository
                .findById(studentId)
                .orElseThrow(() ->
                    new SubscriptionValidationException(
                        "Student does not exist."
                    )
                );

        LocalDate usageDate =
            date == null
                ? LocalDate.now()
                : date;

        List<MealEntitlement> entitlements =
            entitlementRepository
                .findAllBySubscription_Student_IdAndSubscription_StatusAndMealType(
                    student.getId(),
                    SubscriptionStatus.ACTIVE,
                    mealType
                );

        List<MealEntitlement> eligible =
            entitlements
                .stream()
                .filter(entitlement ->
                    covers(
                        entitlement,
                        usageDate
                    )
                )
                .toList();

        if (
            eligible.isEmpty()
        ) {
            throw new SubscriptionValidationException(
                "No active meal entitlement covers "
                    + mealType
                    + " on "
                    + usageDate
                    + "."
            );
        }

        MealEntitlement selected =
            select(eligible, usageDate);

        MealUsage usage =
            new MealUsage(
                selected,
                student,
                mealType,
                usageDate,
                null,
                operator
            );

        try {

            mealUsageRepository
                .saveAndFlush(usage);

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new SubscriptionConflictException(
                "This meal has already been consumed on this day for this service."
            );
        }

        return response(usage);
    }

    private boolean covers(
        MealEntitlement entitlement,
        LocalDate date
    ) {

        if (
            date.isBefore(
                entitlement.getValidFrom()
            )
            || date.isAfter(
                entitlement.getValidTo()
            )
        ) {
            return false;
        }

        Short[] allowedDays =
            entitlement.getAllowedDays();

        if (
            allowedDays == null
            || allowedDays.length == 0
        ) {
            return true;
        }

        short dayOfWeek =
            (short) date.getDayOfWeek()
                .getValue();

        for (
            Short allowed
            : allowedDays
        ) {
            if (
                allowed.equals(dayOfWeek)
            ) {
                return true;
            }
        }

        return false;
    }

    private MealEntitlement select(
        List<MealEntitlement> candidates,
        LocalDate usageDate
    ) {

        for (
            MealEntitlement entitlement
            : candidates
        ) {

            long usedToday =
                mealUsageRepository
                    .countByStudent_IdAndUsageDateAndMealTypeAndStatus(
                        entitlement.getSubscription()
                            .getStudent()
                            .getId(),
                        usageDate,
                        entitlement.getMealType(),
                        MealUsageStatus.VALID
                    );

            if (
                usedToday
                    >= entitlement.getDailyLimit()
            ) {
                continue;
            }

            Integer totalQuota =
                entitlement.getTotalQuota();

            if (
                totalQuota != null
                && consumedInPeriod(
                    entitlement,
                    usageDate
                ) >= totalQuota
            ) {
                continue;
            }

            return entitlement;
        }

        throw new SubscriptionConflictException(
            "Meal quota exhausted for "
                + candidates.get(0)
                    .getMealType()
                + " on "
                + usageDate
                + "."
        );
    }

    private long consumedInPeriod(
        MealEntitlement entitlement,
        LocalDate usageDate
    ) {

        QuotaPeriodType period =
            entitlement.getQuotaPeriodType();

        LocalDate periodStart =
            switch (period) {
                case SUBSCRIPTION -> entitlement
                    .getValidFrom();
                case DAY -> usageDate;
                case WEEK -> usageDate.minusDays(
                    usageDate.getDayOfWeek()
                        .getValue()
                        - 1L
                );
                case MONTH -> usageDate.withDayOfMonth(1);
            };

        return mealUsageRepository
            .findAllByEntitlement_IdAndStatusOrderByUsageDateAsc(
                entitlement.getId(),
                MealUsageStatus.VALID
            )
            .stream()
            .filter(usage ->
                !usage.getUsageDate()
                    .isBefore(periodStart)
            )
            .count();
    }

    private MealUsageResponse response(
        MealUsage usage
    ) {

        return new MealUsageResponse(
            usage.getId(),
            usage.getStudent()
                .getId(),
            usage.getEntitlement()
                .getId(),
            usage.getMealType(),
            usage.getUsageDate(),
            usage.getConsumedAt(),
            usage.getValidatedBy()
                .getId(),
            usage.getStatus()
        );
    }
}