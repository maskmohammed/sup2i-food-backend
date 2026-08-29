package com.sup2i.food.common.api;

import com.sup2i.food.security.exception.AccountBlockedException;
import com.sup2i.food.security.exception.AccountSuspendedException;
import com.sup2i.food.security.exception.LoginRateLimitedException;
import com.sup2i.food.security.exception.MfaAlreadyConfiguredException;
import com.sup2i.food.security.exception.MfaRequiredException;
import com.sup2i.food.security.exception.MfaSetupRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.sup2i.food.catalog.exception.CatalogConflictException;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;
import com.sup2i.food.catalog.exception.CatalogValidationException;
import com.sup2i.food.catalog.exception.ProductUnavailableException;
import com.sup2i.food.identity.exception.RoleNotFoundException;
import com.sup2i.food.identity.exception.UserNotFoundException;
import com.sup2i.food.inventory.exception.InventoryConflictException;
import com.sup2i.food.inventory.exception.InventoryNotFoundException;
import com.sup2i.food.inventory.exception.InventoryValidationException;
import com.sup2i.food.order.exception.OrderConflictException;
import com.sup2i.food.order.exception.OrderNotFoundException;
import com.sup2i.food.order.exception.OrderValidationException;
import com.sup2i.food.payment.exception.PaymentConflictException;
import com.sup2i.food.payment.exception.PaymentNotFoundException;
import com.sup2i.food.payment.exception.PaymentValidationException;
import com.sup2i.food.promotion.exception.CouponIneligibleException;
import com.sup2i.food.promotion.exception.CouponNotFoundException;
import com.sup2i.food.promotion.exception.CouponUsageLimitException;
import com.sup2i.food.promotion.exception.CouponValidationException;
import com.sup2i.food.promotion.exception.LoyaltyConflictException;
import com.sup2i.food.promotion.exception.LoyaltyInsufficientBalanceException;
import com.sup2i.food.promotion.exception.LoyaltyValidationException;
import com.sup2i.food.qr.exception.QrAlreadyUsedException;
import com.sup2i.food.qr.exception.QrConflictException;
import com.sup2i.food.qr.exception.QrExpiredException;
import com.sup2i.food.qr.exception.QrNotFoundException;
import com.sup2i.food.qr.exception.QrRevokedException;
import com.sup2i.food.review.exception.ReviewConflictException;
import com.sup2i.food.review.exception.ReviewNotFoundException;
import com.sup2i.food.review.exception.ReviewValidationException;
import com.sup2i.food.survey.exception.SurveyConflictException;
import com.sup2i.food.survey.exception.SurveyNotFoundException;
import com.sup2i.food.survey.exception.SurveyValidationException;
import com.sup2i.food.menuvote.exception.MenuVoteConflictException;
import com.sup2i.food.menuvote.exception.MenuVoteNotFoundException;
import com.sup2i.food.menuvote.exception.MenuVoteValidationException;
import com.sup2i.food.kitchen.exception.KitchenTicketConflictException;
import com.sup2i.food.kitchen.exception.KitchenTicketNotFoundException;
import com.sup2i.food.notification.exception.NotificationNotFoundException;
import com.sup2i.food.security.exception.PasswordPolicyViolationException;
import com.sup2i.food.security.exception.PasswordResetTokenInvalidException;
import com.sup2i.food.subscription.exception.SubscriptionConflictException;
import com.sup2i.food.subscription.exception.SubscriptionNotFoundException;
import com.sup2i.food.subscription.exception.SubscriptionValidationException;
import com.sup2i.food.timeslot.exception.TimeSlotConflictException;
import com.sup2i.food.timeslot.exception.TimeSlotNotFoundException;
import com.sup2i.food.procurement.exception.SupplierConflictException;
import com.sup2i.food.procurement.exception.SupplierNotFoundException;
import com.sup2i.food.procurement.exception.SupplierValidationException;
import com.sup2i.food.purchase.exception.PurchaseConflictException;
import com.sup2i.food.purchase.exception.PurchaseNotFoundException;
import com.sup2i.food.purchase.exception.PurchaseValidationException;
import com.sup2i.food.waste.exception.WasteConflictException;
import com.sup2i.food.waste.exception.WasteNotFoundException;
import com.sup2i.food.waste.exception.WasteValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger
        log =
            LoggerFactory.getLogger(
                GlobalExceptionHandler.class
            );

    @ExceptionHandler(
        Exception.class
    )
    public ResponseEntity<ApiErrorResponse>
        unexpected(
            Exception exception,
            HttpServletRequest request
        ) {

        log.error(
            "Unhandled exception on [{}] {} (traceId={}). "
                + "Preparing payload for centralized error tracking "
                + "(e.g. Sentry/Datadog).",
            request.getMethod(),
            request.getRequestURI(),
            RequestTrace.resolve(request),
            exception
        );

        return error(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "An unexpected error occurred.",
            request,
            Map.of(),
            exception
        );
    }

    @ExceptionHandler(
        OrderValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        orderValidation(
            OrderValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        OrderNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        orderNotFound(
            OrderNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        OrderConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        orderConflict(
            OrderConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }
    @ExceptionHandler(
        PaymentValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        paymentValidation(
            PaymentValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        PaymentNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        paymentNotFound(
            PaymentNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        PaymentConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        paymentConflict(
            PaymentConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        QrNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        qrNotFound(
            QrNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        QrExpiredException.class
    )
    public ResponseEntity<ApiErrorResponse>
        qrExpired(
            QrExpiredException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        QrRevokedException.class
    )
    public ResponseEntity<ApiErrorResponse>
        qrRevoked(
            QrRevokedException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        QrAlreadyUsedException.class
    )
    public ResponseEntity<ApiErrorResponse>
        qrAlreadyUsed(
            QrAlreadyUsedException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        QrConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        qrConflict(
            QrConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        KitchenTicketNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        kitchenTicketNotFound(
            KitchenTicketNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        KitchenTicketConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        kitchenTicketConflict(
            KitchenTicketConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        TimeSlotNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        timeSlotNotFound(
            TimeSlotNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        TimeSlotConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        timeSlotConflict(
            TimeSlotConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        SubscriptionValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        subscriptionValidation(
            SubscriptionValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        SubscriptionNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        subscriptionNotFound(
            SubscriptionNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        SubscriptionConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        subscriptionConflict(
            SubscriptionConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        UserNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        userNotFound(
            UserNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        RoleNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        roleNotFound(
            RoleNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        PasswordResetTokenInvalidException.class
    )
    public ResponseEntity<ApiErrorResponse>
        passwordResetTokenInvalid(
            PasswordResetTokenInvalidException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        PasswordPolicyViolationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        passwordPolicyViolation(
            PasswordPolicyViolationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        NotificationNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        notificationNotFound(
            NotificationNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        InventoryValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        inventoryValidation(
            InventoryValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        InventoryNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        inventoryNotFound(
            InventoryNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        InventoryConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        inventoryConflict(
            InventoryConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        CatalogValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        catalogValidation(
            CatalogValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }
    @ExceptionHandler(
        CatalogNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        catalogNotFound(
            CatalogNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        CatalogConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        catalogConflict(
            CatalogConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        CouponValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        couponValidation(
            CouponValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        CouponNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        couponNotFound(
            CouponNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        CouponIneligibleException.class
    )
    public ResponseEntity<ApiErrorResponse>
        couponIneligible(
            CouponIneligibleException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "COUPON_INELIGIBLE",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        CouponUsageLimitException.class
    )
    public ResponseEntity<ApiErrorResponse>
        couponUsageLimit(
            CouponUsageLimitException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "USAGE_LIMIT_REACHED",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        LoyaltyValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        loyaltyValidation(
            LoyaltyValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        LoyaltyInsufficientBalanceException.class
    )
    public ResponseEntity<ApiErrorResponse>
        loyaltyInsufficientBalance(
            LoyaltyInsufficientBalanceException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "INSUFFICIENT_LOYALTY_BALANCE",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        LoyaltyConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        loyaltyConflict(
            LoyaltyConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        ProductUnavailableException.class
    )
    public ResponseEntity<ApiErrorResponse>
        productUnavailable(
            ProductUnavailableException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "PRODUCT_UNAVAILABLE",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        ReviewValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        reviewValidation(
            ReviewValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        ReviewNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        reviewNotFound(
            ReviewNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        ReviewConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        reviewConflict(
            ReviewConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        SurveyValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        surveyValidation(
            SurveyValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        SurveyNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        surveyNotFound(
            SurveyNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        SurveyConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        surveyConflict(
            SurveyConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        MenuVoteValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        menuVoteValidation(
            MenuVoteValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        MenuVoteNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        menuVoteNotFound(
            MenuVoteNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        MenuVoteConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        menuVoteConflict(
            MenuVoteConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        SupplierValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        supplierValidation(
            SupplierValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        SupplierNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        supplierNotFound(
            SupplierNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        SupplierConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        supplierConflict(
            SupplierConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        PurchaseValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        purchaseValidation(
            PurchaseValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        PurchaseNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        purchaseNotFound(
            PurchaseNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        PurchaseConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        purchaseConflict(
            PurchaseConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        WasteValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        wasteValidation(
            WasteValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        WasteNotFoundException.class
    )
    public ResponseEntity<ApiErrorResponse>
        wasteNotFound(
            WasteNotFoundException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.NOT_FOUND,
            "NOT_FOUND",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        WasteConflictException.class
    )
    public ResponseEntity<ApiErrorResponse>
        wasteConflict(
            WasteConflictException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "CONFLICT",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        HttpMessageNotReadableException.class
    )
    public ResponseEntity<ApiErrorResponse>
        unreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Request body is invalid or contains an unsupported value.",
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        MethodArgumentNotValidException.class
    )
    public ResponseEntity<ApiErrorResponse>
        validation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
        ) {

        Map<String, Object> fields =
            new LinkedHashMap<>();

        exception
            .getBindingResult()
            .getFieldErrors()
            .forEach(error ->
                fields.put(
                    error.getField(),
                    error.getDefaultMessage()
                )
            );

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Request validation failed.",
            request,
            Map.of(
                "fields",
                fields
            )
        );
    }

    @ExceptionHandler({
        BadCredentialsException.class,
        CredentialsExpiredException.class
    })
    public ResponseEntity<ApiErrorResponse>
        unauthorized(
            RuntimeException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHORIZED",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        AccessDeniedException.class
    )
    public ResponseEntity<ApiErrorResponse>
        permissionDenied(
            AccessDeniedException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.FORBIDDEN,
            "PERMISSION_DENIED",
            "You do not have permission to access this resource.",
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        AccountBlockedException.class
    )
    public ResponseEntity<ApiErrorResponse>
        blocked(
            AccountBlockedException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.FORBIDDEN,
            "ACCOUNT_BLOCKED",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        AccountSuspendedException.class
    )
    public ResponseEntity<ApiErrorResponse>
        suspended(
            AccountSuspendedException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.FORBIDDEN,
            "ACCOUNT_SUSPENDED",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        LoginRateLimitedException.class
    )
    public ResponseEntity<ApiErrorResponse>
        rateLimited(
            LoginRateLimitedException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.TOO_MANY_REQUESTS,
            "RATE_LIMITED",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    @ExceptionHandler(
        MfaRequiredException.class
    )
    public ResponseEntity<ApiErrorResponse>
        mfaRequired(
            MfaRequiredException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.UNAUTHORIZED,
            "MFA_REQUIRED",
            exception.getMessage(),
            request,
            Map.of(
                "methods",
                List.of(
                    "TOTP",
                    "RECOVERY_CODE"
                )
            )
        );
    }

    @ExceptionHandler(
        MfaSetupRequiredException.class
    )
    public ResponseEntity<ApiErrorResponse>
        mfaSetupRequired(
            MfaSetupRequiredException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.FORBIDDEN,
            "MFA_SETUP_REQUIRED",
            exception.getMessage(),
            request,
            Map.of(
                "setupMethod",
                "TOTP"
            )
        );
    }

    @ExceptionHandler(
        MfaAlreadyConfiguredException.class
    )
    public ResponseEntity<ApiErrorResponse>
        mfaAlreadyConfigured(
            MfaAlreadyConfiguredException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.CONFLICT,
            "MFA_ALREADY_CONFIGURED",
            exception.getMessage(),
            request,
            Map.of()
        );
    }

    private ResponseEntity<ApiErrorResponse>
        error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, Object> details
        ) {

        return error(
            status,
            code,
            message,
            request,
            details,
            null
        );
    }

    private ResponseEntity<ApiErrorResponse>
        error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, Object> details,
            Throwable cause
        ) {

        String traceId =
            RequestTrace.resolve(
                request
            );

        if (status.is5xxServerError()) {
            log.error(
                "{} {} -> {} (code={}, traceId={})",
                request.getMethod(),
                request.getRequestURI(),
                status.value(),
                code,
                traceId,
                cause
            );
        } else {
            log.warn(
                "{} {} -> {} (code={}, traceId={})",
                request.getMethod(),
                request.getRequestURI(),
                status.value(),
                code,
                traceId
            );
        }

        ApiErrorResponse body =
            new ApiErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                traceId,
                details
            );

        return ResponseEntity
            .status(status)
            .header(
                RequestTrace.HEADER,
                traceId
            )
            .body(body);
    }
}
