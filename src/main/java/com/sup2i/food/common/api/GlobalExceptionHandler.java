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
import com.sup2i.food.inventory.exception.InventoryConflictException;
import com.sup2i.food.inventory.exception.InventoryNotFoundException;
import com.sup2i.food.inventory.exception.InventoryValidationException;
import com.sup2i.food.order.exception.OrderConflictException;
import com.sup2i.food.order.exception.OrderNotFoundException;
import com.sup2i.food.order.exception.OrderValidationException;
import com.sup2i.food.payment.exception.PaymentConflictException;
import com.sup2i.food.payment.exception.PaymentNotFoundException;
import com.sup2i.food.payment.exception.PaymentValidationException;
import com.sup2i.food.qr.exception.QrAlreadyUsedException;
import com.sup2i.food.qr.exception.QrConflictException;
import com.sup2i.food.qr.exception.QrExpiredException;
import com.sup2i.food.qr.exception.QrNotFoundException;
import com.sup2i.food.qr.exception.QrRevokedException;
import com.sup2i.food.kitchen.exception.KitchenTicketConflictException;
import com.sup2i.food.kitchen.exception.KitchenTicketNotFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
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

        String traceId =
            RequestTrace.resolve(
                request
            );

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
