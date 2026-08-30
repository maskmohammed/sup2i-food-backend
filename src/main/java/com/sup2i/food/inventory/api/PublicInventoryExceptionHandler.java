package com.sup2i.food.inventory.api;

import com.sup2i.food.common.api.ApiErrorResponse;
import com.sup2i.food.common.api.RequestTrace;
import com.sup2i.food.inventory.exception.InventoryPublicErrorCode;
import com.sup2i.food.inventory.exception.InventoryPublicException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(
    assignableTypes = PublicInventoryController.class
)
public class PublicInventoryExceptionHandler {

    @ExceptionHandler(
        InventoryPublicException.class
    )
    public ResponseEntity<ApiErrorResponse> inventory(
        InventoryPublicException exception,
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
            .status(status)
            .header(
                RequestTrace.HEADER,
                traceId
            )
            .body(response);
    }

    @ExceptionHandler(
        MethodArgumentNotValidException.class
    )
    public ResponseEntity<ApiErrorResponse>
        bodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
        ) {

        String traceId =
            RequestTrace.resolve(
                request
            );

        ApiErrorResponse response =
            new ApiErrorResponse(
                OffsetDateTime.now(),
                HttpStatus
                    .UNPROCESSABLE_ENTITY
                    .value(),
                InventoryPublicErrorCode
                    .INVALID_STOCK_ADJUSTMENT
                    .name(),
                "Inventory adjustment request is invalid.",
                request.getRequestURI(),
                traceId,
                Map.of()
            );

        return ResponseEntity
            .status(
                HttpStatus.UNPROCESSABLE_ENTITY
            )
            .header(
                RequestTrace.HEADER,
                traceId
            )
            .body(response);
    }

    private HttpStatus status(
        InventoryPublicErrorCode errorCode
    ) {

        return switch (errorCode) {

            case RESOURCE_NOT_FOUND ->
                HttpStatus.NOT_FOUND;

            case INVALID_STOCK_ADJUSTMENT ->
                HttpStatus.UNPROCESSABLE_ENTITY;

            case IDEMPOTENCY_CONFLICT,
                 CONCURRENT_MODIFICATION,
                 OUT_OF_STOCK ->
                HttpStatus.CONFLICT;
        };
    }
}