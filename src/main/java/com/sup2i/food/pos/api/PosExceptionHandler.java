package com.sup2i.food.pos.api;

import com.sup2i.food.common.api.ApiErrorResponse;
import com.sup2i.food.common.api.RequestTrace;
import com.sup2i.food.pos.exception.PosErrorCode;
import com.sup2i.food.pos.exception.PosException;
import com.sup2i.food.pos.exception.PosValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class PosExceptionHandler {

    @ExceptionHandler(
        PosValidationException.class
    )
    public ResponseEntity<ApiErrorResponse>
        validation(
            PosValidationException exception,
            HttpServletRequest request
        ) {

        return error(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            exception.getMessage(),
            request
        );
    }

    @ExceptionHandler(
        PosException.class
    )
    public ResponseEntity<ApiErrorResponse>
        business(
            PosException exception,
            HttpServletRequest request
        ) {

        return error(
            httpStatus(
                exception.getErrorCode()
            ),
            exception
                .getErrorCode()
                .name(),
            exception.getMessage(),
            request
        );
    }

    private HttpStatus httpStatus(
        PosErrorCode errorCode
    ) {

        return switch (errorCode) {

            case RESOURCE_NOT_FOUND ->
                HttpStatus.NOT_FOUND;

            case CASH_DIFFERENCE_REASON_REQUIRED ->
                HttpStatus.UNPROCESSABLE_CONTENT;

            case POS_SESSION_ALREADY_OPEN,
                 POS_SESSION_NOT_OPEN,
                 IDEMPOTENCY_CONFLICT ->
                HttpStatus.CONFLICT;
        };
    }

    private ResponseEntity<ApiErrorResponse>
        error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
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
                body
            );
    }
}