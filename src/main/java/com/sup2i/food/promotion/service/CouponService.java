package com.sup2i.food.promotion.service;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.catalog.repository.CategoryRepository;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.StudentRepository;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderItem;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.exception.OrderConflictException;
import com.sup2i.food.order.exception.OrderNotFoundException;
import com.sup2i.food.order.repository.OrderItemRepository;
import com.sup2i.food.order.repository.OrderRepository;
import com.sup2i.food.organization.domain.Organization;
import com.sup2i.food.promotion.api.dto.ApplyCouponRequest;
import com.sup2i.food.promotion.api.dto.ApplyCouponResponse;
import com.sup2i.food.promotion.api.dto.CouponResponse;
import com.sup2i.food.promotion.api.dto.CouponSummary;
import com.sup2i.food.promotion.api.dto.CouponValidationRequest;
import com.sup2i.food.promotion.api.dto.CouponValidationResponse;
import com.sup2i.food.promotion.api.dto.CreateCouponRequest;
import com.sup2i.food.promotion.api.dto.UpdateCouponRequest;
import com.sup2i.food.promotion.domain.Coupon;
import com.sup2i.food.promotion.domain.CouponUsage;
import com.sup2i.food.promotion.domain.DiscountSourceType;
import com.sup2i.food.promotion.domain.OrderDiscount;
import com.sup2i.food.promotion.domain.Promotion;
import com.sup2i.food.promotion.domain.PromotionStatus;
import com.sup2i.food.promotion.domain.PromotionTarget;
import com.sup2i.food.promotion.domain.PromotionType;
import com.sup2i.food.promotion.domain.PromotionUsage;
import com.sup2i.food.promotion.domain.TargetType;
import com.sup2i.food.promotion.exception.CouponIneligibleException;
import com.sup2i.food.promotion.exception.CouponNotFoundException;
import com.sup2i.food.promotion.exception.CouponUsageLimitException;
import com.sup2i.food.promotion.exception.CouponValidationException;
import com.sup2i.food.promotion.repository.CouponRepository;
import com.sup2i.food.promotion.repository.CouponUsageRepository;
import com.sup2i.food.promotion.repository.OrderDiscountRepository;
import com.sup2i.food.promotion.repository.PromotionRepository;
import com.sup2i.food.promotion.repository.PromotionTargetRepository;
import com.sup2i.food.promotion.repository.PromotionUsageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class CouponService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionTargetRepository promotionTargetRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final OrderDiscountRepository orderDiscountRepository;

    public CouponService(
        UserRepository userRepository,
        StudentRepository studentRepository,
        ProductRepository productRepository,
        CategoryRepository categoryRepository,
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        PromotionRepository promotionRepository,
        PromotionTargetRepository promotionTargetRepository,
        PromotionUsageRepository promotionUsageRepository,
        CouponRepository couponRepository,
        CouponUsageRepository couponUsageRepository,
        OrderDiscountRepository orderDiscountRepository
    ) {
        this.userRepository =
            userRepository;
        this.studentRepository =
            studentRepository;
        this.productRepository =
            productRepository;
        this.categoryRepository =
            categoryRepository;
        this.orderRepository =
            orderRepository;
        this.orderItemRepository =
            orderItemRepository;
        this.promotionRepository =
            promotionRepository;
        this.promotionTargetRepository =
            promotionTargetRepository;
        this.promotionUsageRepository =
            promotionUsageRepository;
        this.couponRepository =
            couponRepository;
        this.couponUsageRepository =
            couponUsageRepository;
        this.orderDiscountRepository =
            orderDiscountRepository;
    }

    // =========================================================
    // ADMIN OPERATIONS
    // =========================================================

    @Transactional
    public CouponResponse createByAdmin(
        UUID actorId,
        CreateCouponRequest request
    ) {
        User actor =
            requiredUser(actorId);

        Organization organization =
            actor.getOrganization();

        validateDates(
            request.startsAt(),
            request.endsAt()
        );

        validateTargetType(request.targetType());

        validateDiscountValue(
            request.type(),
            request.discountValue()
        );

        List<UUID> targetIds =
            resolveTargetIds(
                request.targetType(),
                request.targetIds(),
                organization.getId()
            );

        String normalizedCode =
            normalizeCode(request.code());

        if (
            couponRepository
                .findByOrganization_IdAndCode(
                    organization.getId(),
                    normalizedCode
                )
                .isPresent()
        ) {
            throw new CouponValidationException(
                "Coupon code already exists."
            );
        }

        Promotion promotion =
            new Promotion(
                organization,
                request.name(),
                normalizedCode,
                request.type(),
                request.startsAt(),
                request.endsAt(),
                actor
            );

        promotion.setStatus(
            PromotionStatus.ACTIVE
        );

        promotion.setDiscountValue(
            request.discountValue()
        );

        promotion.setMaxDiscountAmount(
            request.maxDiscountAmount()
        );

        promotion.setMinQuantity(
            request.minQuantity()
        );

        promotion.setUsageLimitTotal(
            request.usageLimitTotal()
        );

        promotion.setUsageLimitPerStudent(
            request.usageLimitPerStudent()
        );

        promotion.setDescription(
            request.description()
        );

        promotion.setMobileEnabled(true);
        promotion.setPosEnabled(true);

        promotion =
            promotionRepository.save(promotion);

        Coupon coupon =
            new Coupon(
                organization,
                promotion,
                normalizedCode,
                request.startsAt(),
                request.endsAt(),
                request.usageLimitTotal(),
                request.usageLimitPerStudent()
            );

        coupon =
            couponRepository.save(coupon);

        createTargets(
            promotion,
            request.targetType(),
            targetIds
        );

        return response(coupon);
    }

    @Transactional
    public CouponResponse updateByAdmin(
        UUID actorId,
        UUID couponId,
        UpdateCouponRequest request
    ) {
        User actor =
            requiredUser(actorId);

        Coupon coupon =
            ownedCoupon(
                couponId,
                actor.getOrganization()
                    .getId()
            );

        Promotion promotion =
            coupon.getPromotion();

        validateDates(
            request.startsAt(),
            request.endsAt()
        );

        validateTargetType(request.targetType());

        validateDiscountValue(
            promotion.getType(),
            request.discountValue()
        );

        List<UUID> targetIds =
            resolveTargetIds(
                request.targetType(),
                request.targetIds(),
                actor.getOrganization()
                    .getId()
            );

        promotion.setName(request.name());
        promotion.setDescription(request.description());
        promotion.setDiscountValue(request.discountValue());
        promotion.setMaxDiscountAmount(request.maxDiscountAmount());
        promotion.setMinQuantity(request.minQuantity());
        promotion.setUsageLimitTotal(request.usageLimitTotal());
        promotion.setUsageLimitPerStudent(request.usageLimitPerStudent());
        promotion.setStartsAt(request.startsAt());
        promotion.setEndsAt(request.endsAt());

        promotionRepository.save(promotion);

        coupon.setStartsAt(request.startsAt());
        coupon.setEndsAt(request.endsAt());
        coupon.setMaxUses(request.usageLimitTotal());
        coupon.setMaxUsesPerStudent(request.usageLimitPerStudent());

        couponRepository.save(coupon);

        promotionTargetRepository
            .deleteAllByPromotion_Id(
                promotion.getId()
            );

        createTargets(
            promotion,
            request.targetType(),
            targetIds
        );

        return response(coupon);
    }

    @Transactional
    public CouponResponse deactivateByAdmin(
        UUID actorId,
        UUID couponId
    ) {
        User actor =
            requiredUser(actorId);

        Coupon coupon =
            ownedCoupon(
                couponId,
                actor.getOrganization()
                    .getId()
            );

        coupon.setActive(false);

        Promotion promotion =
            coupon.getPromotion();

        promotion.setStatus(
            PromotionStatus.PAUSED
        );

        couponRepository.save(coupon);
        promotionRepository.save(promotion);

        return response(coupon);
    }

    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> listByAdmin(
        UUID actorId,
        int page,
        int size
    ) {
        User actor =
            requiredUser(actorId);

        Page<Coupon> coupons =
            couponRepository
                .findByOrganization_Id(
                    actor.getOrganization()
                        .getId(),
                    PageRequest.of(
                        page,
                        size,
                        Sort.by(
                            Sort.Direction.DESC,
                            "createdAt"
                        )
                    )
                );

        return PageResponse.from(
            coupons.map(this::response)
        );
    }

    @Transactional(readOnly = true)
    public CouponResponse getByAdmin(
        UUID actorId,
        UUID couponId
    ) {
        User actor =
            requiredUser(actorId);

        return response(
            ownedCoupon(
                couponId,
                actor.getOrganization()
                    .getId()
            )
        );
    }

    // =========================================================
    // STUDENT OPERATIONS
    // =========================================================

    @Transactional(readOnly = true)
    public CouponValidationResponse validate(
        UUID actorId,
        CouponValidationRequest request
    ) {
        Student student =
            requiredStudent(actorId);

        UUID organizationId =
            organizationId(student);

        Order order =
            orderRepository
                .findByIdAndOrganization_IdAndStudent_Id(
                    request.orderId(),
                    organizationId,
                    student.getId()
                )
                .orElseThrow(() ->
                    new OrderNotFoundException(
                        "Order does not exist."
                    )
                );

        if (
            order.getStatus()
                != OrderStatus.DRAFT
        ) {
            return new CouponValidationResponse(
                false,
                "Only a draft order can receive a coupon.",
                null,
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2)
            );
        }

        String normalizedCode =
            normalizeCode(request.code());

        Coupon coupon =
            couponRepository
                .findByOrganization_IdAndCode(
                    organizationId,
                    normalizedCode
                )
                .orElse(null);

        if (
            coupon == null
        ) {
            return new CouponValidationResponse(
                false,
                "Coupon code does not exist.",
                null,
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2)
            );
        }

        EligibilityCheck check =
            evaluate(
                coupon,
                order,
                student,
                OffsetDateTime.now(),
                true
            );

        if (
            !check.eligible()
        ) {
            return new CouponValidationResponse(
                false,
                check.reason(),
                null,
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2)
            );
        }

        Promotion promotion =
            coupon.getPromotion();

        return new CouponValidationResponse(
            true,
            null,
            summary(coupon, promotion),
            check.eligibleAmount(),
            check.discountAmount()
        );
    }

    @Transactional
    public ApplyCouponResponse apply(
        UUID actorId,
        ApplyCouponRequest request
    ) {
        Student student =
            requiredStudent(actorId);

        UUID organizationId =
            organizationId(student);

        Order order =
            orderRepository
                .findStudentOwnedForUpdate(
                    request.orderId(),
                    organizationId,
                    student.getId()
                )
                .orElseThrow(() ->
                    new OrderNotFoundException(
                        "Order does not exist."
                    )
                );

        if (
            order.getStatus()
                != OrderStatus.DRAFT
        ) {
            throw new OrderConflictException(
                "Only a draft order can receive a coupon."
            );
        }

        Coupon coupon =
            couponRepository
                .findByOrganization_IdAndCode(
                    organizationId,
                    normalizeCode(request.code())
                )
                .orElseThrow(() ->
                    new CouponNotFoundException(
                        "Coupon code does not exist."
                    )
                );

        OffsetDateTime now =
            OffsetDateTime.now();

        EligibilityCheck check =
            evaluate(
                coupon,
                order,
                student,
                now,
                true
            );

        if (
            !check.eligible()
        ) {
            if (
                check.usageLimitReached()
            ) {
                throw new CouponUsageLimitException(
                    check.reason()
                );
            }

            throw new CouponIneligibleException(
                check.reason()
            );
        }

        Promotion promotion =
            coupon.getPromotion();

        BigDecimal discount =
            check.discountAmount();

        order.applyDiscount(discount);

        orderRepository.save(order);

        orderDiscountRepository.save(
            new OrderDiscount(
                order,
                DiscountSourceType.COUPON,
                coupon.getId(),
                coupon.getCode(),
                label(promotion, coupon),
                discount,
                "Applied coupon " + coupon.getCode()
            )
        );

        couponUsageRepository.save(
            new CouponUsage(
                coupon,
                order,
                student
            )
        );

        promotionUsageRepository.save(
            new PromotionUsage(
                promotion,
                order,
                student,
                discount
            )
        );

        return new ApplyCouponResponse(
            order.getId(),
            coupon.getCode(),
            discount,
            order.getDiscountTotal(),
            order.getSubtotal(),
            order.getTotal()
        );
    }

    // =========================================================
    // ELIGIBILITY ENGINE
    // =========================================================

    private EligibilityCheck evaluate(
        Coupon coupon,
        Order order,
        Student student,
        OffsetDateTime now,
        boolean checkAlreadyApplied
    ) {
        Promotion promotion =
            coupon.getPromotion();

        if (
            !coupon.isActive()
        ) {
            return ineligible(
                "Coupon is not active.",
                false
            );
        }

        if (
            promotion.getStatus()
                != PromotionStatus.ACTIVE
        ) {
            return ineligible(
                "Coupon is not active.",
                false
            );
        }

        OffsetDateTime startsAt =
            coupon.getStartsAt() != null
                ? coupon.getStartsAt()
                : promotion.getStartsAt();

        OffsetDateTime endsAt =
            coupon.getEndsAt() != null
                ? coupon.getEndsAt()
                : promotion.getEndsAt();

        if (
            startsAt != null
                && now.isBefore(startsAt)
        ) {
            return ineligible(
                "Coupon is not active yet.",
                false
            );
        }

        if (
            endsAt != null
                && now.isAfter(endsAt)
        ) {
            return ineligible(
                "Coupon has expired.",
                false
            );
        }

        if (
            !promotion.isMobileEnabled()
        ) {
            return ineligible(
                "Coupon is not available on mobile orders.",
                false
            );
        }

        if (
            checkAlreadyApplied
                && couponUsageRepository
                    .existsByCoupon_IdAndOrder_Id(
                        coupon.getId(),
                        order.getId()
                    )
        ) {
            return ineligible(
                "Coupon has already been applied to this order.",
                false
            );
        }

        if (
            coupon.getMaxUses() != null
                && couponUsageRepository
                    .countByCoupon_Id(
                        coupon.getId()
                    )
                    >= coupon.getMaxUses()
        ) {
            return ineligible(
                "Coupon usage limit has been reached.",
                true
            );
        }

        if (
            coupon.getMaxUsesPerStudent() != null
                && student != null
                && couponUsageRepository
                    .countByCoupon_IdAndStudent_Id(
                        coupon.getId(),
                        student.getId()
                    )
                    >= coupon.getMaxUsesPerStudent()
        ) {
            return ineligible(
                "Coupon usage limit per student has been reached.",
                true
            );
        }

        List<PromotionTarget> targets =
            promotionTargetRepository
                .findByPromotion_Id(
                    promotion.getId()
                );

        List<OrderItem> items =
            orderItemRepository
                .findAllByOrder_IdOrderByIdAsc(
                    order.getId()
                );

        int totalQuantity =
            items.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();

        if (
            promotion.getMinQuantity() != null
                && totalQuantity
                    < promotion.getMinQuantity()
        ) {
            return ineligible(
                "Order quantity is below the coupon minimum.",
                false
            );
        }

        BigDecimal eligibleAmount =
            BigDecimal.ZERO.setScale(2);

        for (
            OrderItem item
            : items
        ) {
            if (
                matchesTarget(
                    item,
                    targets
                )
            ) {
                eligibleAmount =
                    eligibleAmount.add(
                        item.getLineTotal()
                    );
            }
        }

        if (
            eligibleAmount.signum() <= 0
        ) {
            return ineligible(
                "Coupon does not apply to any item in this order.",
                false
            );
        }

        CouponEvaluation evaluation =
            CouponCalculator.evaluate(
                promotion.getType(),
                promotion.getDiscountValue(),
                promotion.getMaxDiscountAmount(),
                eligibleAmount
            );

        if (
            evaluation.discountAmount()
                .signum() <= 0
        ) {
            return ineligible(
                "Coupon would not reduce the order total.",
                false
            );
        }

        return new EligibilityCheck(
            true,
            null,
            false,
            evaluation.eligibleAmount(),
            evaluation.discountAmount()
        );
    }

    private boolean matchesTarget(
        OrderItem item,
        List<PromotionTarget> targets
    ) {
        for (
            PromotionTarget target
            : targets
        ) {
            if (
                target.getTargetType()
                    == TargetType.ALL
            ) {
                return true;
            }

            if (
                target.getTargetType()
                    == TargetType.PRODUCT
                && target.getTargetId()
                    != null
                && target.getTargetId()
                    .equals(
                        item.getProduct()
                            .getId()
                    )
            ) {
                return true;
            }

            if (
                target.getTargetType()
                    == TargetType.CATEGORY
                && target.getTargetId()
                    != null
                && target.getTargetId()
                    .equals(
                        item.getProduct()
                            .getCategory()
                            .getId()
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private EligibilityCheck ineligible(
        String reason,
        boolean usageLimitReached
    ) {
        return new EligibilityCheck(
            false,
            reason,
            usageLimitReached,
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2)
        );
    }

    private record EligibilityCheck(
        boolean eligible,
        String reason,
        boolean usageLimitReached,
        BigDecimal eligibleAmount,
        BigDecimal discountAmount
    ) {
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private User requiredUser(
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

    private Student requiredStudent(
        UUID actorId
    ) {
        return studentRepository
            .findByUserId(actorId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated student does not exist."
                )
            );
    }

    private UUID organizationId(
        Student student
    ) {
        return student.getCampus()
            .getOrganization()
            .getId();
    }

    private Coupon ownedCoupon(
        UUID couponId,
        UUID organizationId
    ) {
        Coupon coupon =
            couponRepository
                .findById(couponId)
                .orElseThrow(() ->
                    new CouponNotFoundException(
                        "Coupon does not exist."
                    )
                );

        if (
            !coupon.getOrganization()
                .getId()
                .equals(organizationId)
        ) {
            throw new CouponNotFoundException(
                "Coupon does not exist."
            );
        }

        return coupon;
    }

    private String normalizeCode(
        String code
    ) {
        return code.trim()
            .toUpperCase(Locale.ROOT);
    }

    private void validateDates(
        OffsetDateTime startsAt,
        OffsetDateTime endsAt
    ) {
        if (
            startsAt == null
                || endsAt == null
        ) {
            throw new CouponValidationException(
                "Coupon dates are required."
            );
        }

        if (
            !endsAt.isAfter(startsAt)
        ) {
            throw new CouponValidationException(
                "Coupon endsAt must be after startsAt."
            );
        }
    }

    private void validateTargetType(
        TargetType targetType
    ) {
        if (
            targetType != TargetType.ALL
                && targetType != TargetType.PRODUCT
                && targetType != TargetType.CATEGORY
        ) {
            throw new CouponValidationException(
                "Coupon targets must be ALL, CATEGORY or PRODUCT."
            );
        }
    }

    private void validateDiscountValue(
        PromotionType type,
        BigDecimal discountValue
    ) {
        if (
            discountValue == null
        ) {
            throw new CouponValidationException(
                "Coupon discount value is required."
            );
        }

        if (
            type == PromotionType.PERCENTAGE
                && discountValue.compareTo(
                    BigDecimal.valueOf(100)
                ) > 0
        ) {
            throw new CouponValidationException(
                "Percentage discount cannot exceed 100."
            );
        }
    }

    private List<UUID> resolveTargetIds(
        TargetType targetType,
        List<UUID> rawTargetIds,
        UUID organizationId
    ) {
        if (
            targetType == TargetType.ALL
        ) {
            return List.of();
        }

        List<UUID> ids =
            rawTargetIds == null
                ? List.of()
                : rawTargetIds;

        if (
            ids.isEmpty()
        ) {
            throw new CouponValidationException(
                "Coupon target ids are required."
            );
        }

        List<UUID> resolved =
            new ArrayList<>();

        for (
            UUID targetId
            : ids
        ) {
            if (
                targetType == TargetType.PRODUCT
            ) {
                productRepository
                    .findCatalogProduct(
                        targetId,
                        organizationId
                    )
                    .orElseThrow(() ->
                        new CouponValidationException(
                            "Product target does not exist in this organization."
                        )
                    );
            } else {
                categoryRepository
                    .findByIdAndOrganization_Id(
                        targetId,
                        organizationId
                    )
                    .orElseThrow(() ->
                        new CouponValidationException(
                            "Category target does not exist in this organization."
                        )
                    );
            }

            resolved.add(targetId);
        }

        return resolved;
    }

    private void createTargets(
        Promotion promotion,
        TargetType targetType,
        List<UUID> targetIds
    ) {
        if (
            targetType == TargetType.ALL
        ) {
            promotionTargetRepository.save(
                new PromotionTarget(
                    promotion,
                    TargetType.ALL,
                    null
                )
            );
            return;
        }

        for (
            UUID targetId
            : targetIds
        ) {
            promotionTargetRepository.save(
                new PromotionTarget(
                    promotion,
                    targetType,
                    targetId
                )
            );
        }
    }

    private String label(
        Promotion promotion,
        Coupon coupon
    ) {
        return promotion.getName()
            + " ("
            + coupon.getCode()
            + ")";
    }

    private CouponSummary summary(
        Coupon coupon,
        Promotion promotion
    ) {
        List<PromotionTarget> targets =
            promotionTargetRepository
                .findByPromotion_Id(
                    promotion.getId()
                );

        TargetType targetType =
            targets.isEmpty()
                ? TargetType.ALL
                : targets.get(0)
                    .getTargetType();

        List<UUID> targetIds =
            targets.stream()
                .map(PromotionTarget::getTargetId)
                .filter(Objects::nonNull)
                .toList();

        return new CouponSummary(
            coupon.getId(),
            coupon.getCode(),
            label(promotion, coupon),
            promotion.getType(),
            promotion.getDiscountValue(),
            promotion.getMaxDiscountAmount(),
            targetType,
            targetIds,
            promotion.getStartsAt(),
            promotion.getEndsAt()
        );
    }

    private CouponResponse response(
        Coupon coupon
    ) {
        Promotion promotion =
            coupon.getPromotion();

        List<PromotionTarget> targets =
            promotionTargetRepository
                .findByPromotion_Id(
                    promotion.getId()
                );

        TargetType targetType =
            targets.isEmpty()
                ? TargetType.ALL
                : targets.get(0)
                    .getTargetType();

        List<UUID> targetIds =
            targets.stream()
                .map(PromotionTarget::getTargetId)
                .filter(Objects::nonNull)
                .toList();

        return new CouponResponse(
            coupon.getId(),
            coupon.getCode(),
            promotion.getName(),
            promotion.getDescription(),
            promotion.getType(),
            promotion.getStatus(),
            promotion.getDiscountValue(),
            promotion.getMaxDiscountAmount(),
            promotion.getMinQuantity(),
            promotion.getUsageLimitTotal(),
            promotion.getUsageLimitPerStudent(),
            targetType,
            targetIds,
            promotion.getStartsAt(),
            promotion.getEndsAt(),
            coupon.isActive(),
            promotion.isMobileEnabled()
        );
    }
}