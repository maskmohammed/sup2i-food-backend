package com.sup2i.food.payment.api;

import com.sup2i.food.common.api.ApiErrorResponse;
import com.sup2i.food.common.api.RequestTrace;
import com.sup2i.food.payment.exception.RefundErrorCode;
import com.sup2i.food.payment.exception.RefundException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice(
    assignableTypes =
        PaymentRefundController.class
)
public class RefundExceptionHandler {

    @ExceptionHandler(
        RefundException.class
    )
    public ResponseEntity<ApiErrorResponse> refundException(
        RefundException exception,
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
        RefundErrorCode errorCode
    ) {
        return switch (errorCode) {

            case VALIDATION_ERROR ->
                HttpStatus.BAD_REQUEST;

            case RESOURCE_NOT_FOUND ->
                HttpStatus.NOT_FOUND;

            case REFUND_AMOUNT_EXCEEDED ->
                HttpStatus.UNPROCESSABLE_ENTITY;

            case IDEMPOTENCY_CONFLICT,
                 PAYMENT_NOT_REFUNDABLE ->
                HttpStatus.CONFLICT;
        };
    }
}