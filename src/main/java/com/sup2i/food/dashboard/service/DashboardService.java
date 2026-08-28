package com.sup2i.food.dashboard.service;

import com.sup2i.food.dashboard.api.dto.DashboardSummaryResponse;
import com.sup2i.food.dashboard.api.dto.OrderStatusCountResponse;
import com.sup2i.food.dashboard.api.dto.RevenueSummaryResponse;
import com.sup2i.food.dashboard.api.dto.TopProductResponse;
import com.sup2i.food.dashboard.repository.DashboardRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {

    private static final int
        TOP_PRODUCTS_LIMIT =
            10;

    private final DashboardRepository dashboardRepository;
    private final UserRepository userRepository;

    public DashboardService(
        DashboardRepository dashboardRepository,
        UserRepository userRepository
    ) {
        this.dashboardRepository =
            dashboardRepository;

        this.userRepository =
            userRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(
        UUID actorId
    ) {

        UUID organizationId =
            resolveOrganizationId(
                actorId
            );

        OffsetDateTime now =
            OffsetDateTime.now();

        LocalDate today =
            now.toLocalDate();

        OffsetDateTime startOfToday =
            today.atStartOfDay(
                now.getOffset()
            ).toOffsetDateTime();

        OffsetDateTime startOfWeek =
            today.with(
                    DayOfWeek.MONDAY
                )
                .atStartOfDay(
                    now.getOffset()
                )
                .toOffsetDateTime();

        OffsetDateTime startOfMonth =
            today.withDayOfMonth(1)
                .atStartOfDay(
                    now.getOffset()
                )
                .toOffsetDateTime();

        RevenueSummaryResponse revenue =
            new RevenueSummaryResponse(
                dashboardRepository
                    .revenueBetween(
                        organizationId,
                        startOfToday,
                        now
                    ),
                dashboardRepository
                    .revenueBetween(
                        organizationId,
                        startOfWeek,
                        now
                    ),
                dashboardRepository
                    .revenueBetween(
                        organizationId,
                        startOfMonth,
                        now
                    )
            );

        List<OrderStatusCountResponse>
            ordersByStatus =
                dashboardRepository
                    .orderCountsByStatus(
                        organizationId
                    )
                    .stream()
                    .map(entry ->
                        new OrderStatusCountResponse(
                            entry.status(),
                            entry.count()
                        )
                    )
                    .toList();

        List<TopProductResponse>
            topProducts =
                dashboardRepository
                    .topProducts(
                        organizationId,
                        TOP_PRODUCTS_LIMIT
                    )
                    .stream()
                    .map(entry ->
                        new TopProductResponse(
                            entry.productId(),
                            entry.productName(),
                            entry.quantitySold()
                        )
                    )
                    .toList();

        Double averagePreparationSeconds =
            dashboardRepository
                .averagePreparationSeconds(
                    organizationId
                );

        return new DashboardSummaryResponse(
            revenue,
            dashboardRepository
                .averageBasket(
                    organizationId
                ),
            ordersByStatus,
            topProducts,
            averagePreparationSeconds
                == null
                    ? null
                    : averagePreparationSeconds
                        / 60.0
        );
    }

    private UUID resolveOrganizationId(
        UUID actorId
    ) {

        User actor =
            userRepository
                .findById(actorId)
                .orElseThrow(() ->
                    new BadCredentialsException(
                        "Authenticated user does not exist."
                    )
                );

        return actor.getOrganization()
            .getId();
    }
}
