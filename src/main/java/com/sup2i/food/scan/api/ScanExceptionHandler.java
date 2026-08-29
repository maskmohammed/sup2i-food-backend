package com.sup2i.food.scan.api;

import com.sup2i.food.common.api.ApiErrorResponse;
import com.sup2i.food.common.api.RequestTrace;
import com.sup2i.food.scan.exception.ScanException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice(
    assignableTypes = ScanController.class
)
public class ScanExceptionHandler {

    @ExceptionHandler(
        ScanException.class
    )
    public ResponseEntity<ApiErrorResponse>
        scan(
            ScanException exception,
            HttpServletRequest request
        ) {

        HttpStatus status =
            switch (
                exception.errorCode()
            ) {
                case RESOURCE_NOT_FOUND,
                     INVALID_QR ->
                    HttpStatus.NOT_FOUND;

                case QR_EXPIRED,
                     QR_REVOKED ->
                    HttpStatus.CONFLICT;
            };

        String traceId =
            RequestTrace.resolve(
                request
            );

        ApiErrorResponse body =
            new ApiErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                exception
                    .errorCode()
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
            .body(body);
    }
}