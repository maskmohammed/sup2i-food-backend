package com.sup2i.food.promotion.service;

import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.exception.UserNotFoundException;
import com.sup2i.food.identity.repository.StudentRepository;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.exception.OrderConflictException;
import com.sup2i.food.order.exception.OrderNotFoundException;
import com.sup2i.food.order.repository.OrderRepository;
import com.sup2i.food.promotion.api.dto.AdminLoyaltyAdjustRequest;
import com.sup2i.food.promotion.api.dto.LoyaltyAdjustResponse;
import com.sup2i.food.promotion.api.dto.LoyaltyBalanceResponse;
import com.sup2i.food.promotion.api.dto.LoyaltyRedeemRequest;
import com.sup2i.food.promotion.api.dto.LoyaltyRedeemResponse;
import com.sup2i.food.promotion.domain.DiscountSourceType;
import com.sup2i.food.promotion.domain.LoyaltyAccount;
import com.sup2i.food.promotion.domain.LoyaltyAccountStatus;
import com.sup2i.food.promotion.domain.LoyaltyTransaction;
import com.sup2i.food.promotion.domain.LoyaltyTransactionType;
import com.sup2i.food.promotion.domain.OrderDiscount;
import com.sup2i.food.promotion.exception.LoyaltyConflictException;
import com.sup2i.food.promotion.exception.LoyaltyInsufficientBalanceException;
import com.sup2i.food.promotion.exception.LoyaltyValidationException;
import com.sup2i.food.promotion.repository.LoyaltyAccountRepository;
import com.sup2i.food.promotion.repository.LoyaltyTransactionRepository;
import com.sup2i.food.promotion.repository.OrderDiscountRepository;
import com.sup2i.food.subscription.domain.Subscription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class LoyaltyService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final OrderRepository orderRepository;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final OrderDiscountRepository orderDiscountRepository;

    private final BigDecimal madPerPoint;
    private final int pointsPerReward;
    private final BigDecimal rewardValueMad;

    public LoyaltyService(
        UserRepository userRepository,
        StudentRepository studentRepository,
        OrderRepository orderRepository,
        LoyaltyAccountRepository loyaltyAccountRepository,
        LoyaltyTransactionRepository loyaltyTransactionRepository,
        OrderDiscountRepository orderDiscountRepository,
        @Value("${sup2i.loyalty.mad-per-point:10}")
        BigDecimal madPerPoint,
        @Value("${sup2i.loyalty.points-per-reward:100}")
        int pointsPerReward,
        @Value("${sup2i.loyalty.reward-value-mad:5.00}")
        BigDecimal rewardValueMad
    ) {
        this.userRepository =
            userRepository;
        this.studentRepository =
            studentRepository;
        this.orderRepository =
            orderRepository;
        this.loyaltyAccountRepository =
            loyaltyAccountRepository;
        this.loyaltyTransactionRepository =
            loyaltyTransactionRepository;
        this.orderDiscountRepository =
            orderDiscountRepository;
        this.madPerPoint =
            madPerPoint;
        this.pointsPerReward =
            pointsPerReward;
        this.rewardValueMad =
            rewardValueMad;
    }

    // =========================================================
    // STUDENT OPERATIONS
    // =========================================================

    public LoyaltyBalanceResponse getBalance(
        UUID actorId
    ) {
        Student student =
            requiredStudent(actorId);

        return loyaltyAccountRepository
            .findByStudent_Id(
                student.getId()
            )
            .map(account ->
                new LoyaltyBalanceResponse(
                    account.getStatus().name(),
                    account.getCurrentBalance(),
                    account.getLifetimeEarned(),
                    account.getLifetimeRedeemed()
                )
            )
            .orElseGet(() ->
                new LoyaltyBalanceResponse(
                    LoyaltyAccountStatus.ACTIVE.name(),
                    0,
                    0,
                    0
                )
            );
    }

    @Transactional
    public LoyaltyRedeemResponse redeem(
        UUID actorId,
        LoyaltyRedeemRequest request
    ) {
        Student student =
            requiredStudent(actorId);

        Order order =
            draftOrder(
                student,
                request.orderId()
            );

        if (
            request.points()
                < pointsPerReward
        ) {
            throw new LoyaltyValidationException(
                "At least "
                    + pointsPerReward
                    + " points are required to redeem."
            );
        }

        LoyaltyRedemption redemption =
            LoyaltyCalculator.redeem(
                request.points(),
                pointsPerReward,
                rewardValueMad
            );

        if (
            redemption.usedPoints() <= 0
                || redemption.rewardValue()
                    .signum() <= 0
        ) {
            throw new LoyaltyValidationException(
                "Points cannot be redeemed into a reward."
            );
        }

        LoyaltyAccount account =
            loyaltyAccountRepository
                .findByStudent_IdForUpdate(
                    student.getId()
                )
                .orElseThrow(() ->
                    new LoyaltyInsufficientBalanceException(
                        "No loyalty points available."
                    )
                );

        if (
            account.getStatus()
                != LoyaltyAccountStatus.ACTIVE
        ) {
            throw new LoyaltyConflictException(
                "Loyalty account is not active."
            );
        }

        if (
            account.getCurrentBalance()
                < redemption.usedPoints()
        ) {
            throw new LoyaltyInsufficientBalanceException(
                "Not enough loyalty points."
            );
        }

        BigDecimal reward =
            redemption.rewardValue();

        order.applyDiscount(reward);

        orderRepository.save(order);

        orderDiscountRepository.save(
            new OrderDiscount(
                order,
                DiscountSourceType.LOYALTY,
                account.getId(),
                null,
                "Loyalty reward ("
                    + redemption.usedPoints()
                    + " points)",
                reward,
                "Redeemed "
                    + redemption.usedPoints()
                    + " loyalty points against order."
            )
        );

        account.redeem(
            redemption.usedPoints()
        );

        loyaltyAccountRepository.save(account);

        loyaltyTransactionRepository.save(
            new LoyaltyTransaction(
                account,
                LoyaltyTransactionType.REDEEM,
                -redemption.usedPoints(),
                "ORDER",
                order.getId(),
                "Points redeemed against order.",
                actorId,
                order.getId()
            )
        );

        return new LoyaltyRedeemResponse(
            order.getId(),
            redemption.usedPoints(),
            reward,
            account.getCurrentBalance(),
            order.getDiscountTotal(),
            order.getTotal()
        );
    }

    // =========================================================
    // EARNING HOOKS (called from order/subscription services)
    // =========================================================

    public void earnForOrder(
        Order order,
        UUID actorId
    ) {
        if (
            order.getStudent() == null
        ) {
            return;
        }

        int points =
            LoyaltyCalculator.earnedPoints(
                order.getTotal(),
                madPerPoint
            );

        if (
            points <= 0
        ) {
            return;
        }

        LoyaltyAccount account =
            findOrCreate(order.getStudent());

        account.earn(points);

        loyaltyAccountRepository.save(account);

        loyaltyTransactionRepository.save(
            new LoyaltyTransaction(
                account,
                LoyaltyTransactionType.EARN,
                points,
                "ORDER",
                order.getId(),
                "Earned points for paid order.",
                actorId,
                order.getId()
            )
        );
    }

    public void earnForSubscription(
        Subscription subscription,
        UUID actorId
    ) {
        if (
            subscription.getStudent() == null
                || subscription.getAdministrativePaymentAmount()
                    == null
        ) {
            return;
        }

        int points =
            LoyaltyCalculator.earnedPoints(
                subscription.getAdministrativePaymentAmount(),
                madPerPoint
            );

        if (
            points <= 0
        ) {
            return;
        }

        LoyaltyAccount account =
            findOrCreate(
                subscription.getStudent()
            );

        account.earn(points);

        loyaltyAccountRepository.save(account);

        loyaltyTransactionRepository.save(
            new LoyaltyTransaction(
                account,
                LoyaltyTransactionType.EARN,
                points,
                "SUBSCRIPTION",
                subscription.getId(),
                "Earned points for activated subscription.",
                actorId,
                null
            )
        );
    }

    // =========================================================
    // ADMIN OPERATIONS
    // =========================================================

    @Transactional
    public LoyaltyAdjustResponse adjustByAdmin(
        UUID actorId,
        AdminLoyaltyAdjustRequest request
    ) {
        User actor =
            requiredUser(actorId);

        if (
            request.points() == 0
        ) {
            throw new LoyaltyValidationException(
                "Points adjustment cannot be zero."
            );
        }

        Student student =
            studentRepository
                .findByUserId(
                    request.studentUserId()
                )
                .orElseThrow(() ->
                    new UserNotFoundException(
                        "Student user does not exist."
                    )
                );

        if (
            !student.getCampus()
                .getOrganization()
                .getId()
                .equals(
                    actor.getOrganization()
                        .getId()
                )
        ) {
            throw new LoyaltyConflictException(
                "Student does not belong to this organization."
            );
        }

        LoyaltyAccount account =
            loyaltyAccountRepository
                .findByStudent_IdForUpdate(
                    student.getId()
                )
                .orElseGet(() ->
                    loyaltyAccountRepository.save(
                        new LoyaltyAccount(student)
                    )
                );

        int adjusted =
            request.points();

        if (
            adjusted < 0
                && account.getCurrentBalance()
                    + adjusted < 0
        ) {
            throw new LoyaltyInsufficientBalanceException(
                "Adjustment would result in a negative balance."
            );
        }

        account.adjust(adjusted);

        loyaltyAccountRepository.save(account);

        loyaltyTransactionRepository.save(
            new LoyaltyTransaction(
                account,
                LoyaltyTransactionType.ADJUSTMENT,
                adjusted,
                "MANUAL",
                null,
                request.reason(),
                actor.getId(),
                null
            )
        );

        return new LoyaltyAdjustResponse(
            request.studentUserId(),
            adjusted,
            account.getCurrentBalance()
        );
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

    private Order draftOrder(
        Student student,
        UUID orderId
    ) {
        Order order =
            orderRepository
                .findStudentOwnedForUpdate(
                    orderId,
                    organizationId(student),
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
                "Only a draft order can be modified."
            );
        }

        return order;
    }

    private UUID organizationId(
        Student student
    ) {
        return student.getCampus()
            .getOrganization()
            .getId();
    }

    private LoyaltyAccount findOrCreate(
        Student student
    ) {
        return loyaltyAccountRepository
            .findByStudent_Id(student.getId())
            .orElseGet(() ->
                loyaltyAccountRepository.save(
                    new LoyaltyAccount(student)
                )
            );
    }
}