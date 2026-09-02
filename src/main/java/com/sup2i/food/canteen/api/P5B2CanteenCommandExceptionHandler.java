package com.sup2i.food.canteen.api;

import com.sup2i.food.canteen.exception.CanteenErrorCode;
import com.sup2i.food.canteen.exception.CanteenException;
import com.sup2i.food.common.api.ApiErrorResponse;
import com.sup2i.food.common.api.RequestTrace;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice(
    assignableTypes = {
        AdminFoodPassReplacementController.class,
        CanteenCancellationController.class
    }
)
public class P5B2CanteenCommandExceptionHandler {

    @ExceptionHandler(
        CanteenException.class
    )
    public ResponseEntity<ApiErrorResponse> canteenException(
        CanteenException exception,
        HttpServletRequest request
    ) {
        HttpStatus status =
            status(
                exception.getErrorCode()
            );

        String traceId =
            RequestTrace.resolve(
                request
            );

        ApiErrorResponse response =
            new ApiErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                exception
                    .getErrorCode()
                    .name(),
                exception.getMessage(),
                request.getRequestURI(),
                traceId,
                Map.of()
            );

        return ResponseEntity
            .status(
                status
            )
            .header(
                RequestTrace.HEADER,
                traceId
            )
            .body(
                response
            );
    }

    private HttpStatus status(
        CanteenErrorCode errorCode
    ) {
        return switch (errorCode) {

            case VALIDATION_ERROR ->
                HttpStatus.BAD_REQUEST;

            case RESOURCE_NOT_FOUND,
                 INVALID_QR ->
                HttpStatus.NOT_FOUND;

            case MEAL_NOT_ALLOWED ->
                HttpStatus.UNPROCESSABLE_ENTITY;

            case IDEMPOTENCY_CONFLICT,
                 CANTEEN_RESERVATION_CLOSED,
                 CANTEEN_ALREADY_RESERVED,
                 FOOD_PASS_BLOCKED,
                 FOOD_PASS_LOST,
                 FOOD_PASS_REVOKED,
                 FOOD_PASS_EXPIRED,
                 STUDENT_INACTIVE,
                 SUBSCRIPTION_INACTIVE,
                 ENTITLEMENT_EXPIRED,
                 QUOTA_EXHAUSTED,
                 DAILY_LIMIT_REACHED,
                 MEAL_ALREADY_USED ->
                HttpStatus.CONFLICT;
        };
    }
}