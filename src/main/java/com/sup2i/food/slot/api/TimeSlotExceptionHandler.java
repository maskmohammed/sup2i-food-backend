package com.sup2i.food.slot.api;

import com.sup2i.food.common.api.ApiErrorResponse;
import com.sup2i.food.common.api.RequestTrace;
import com.sup2i.food.order.api.OrderController;
import com.sup2i.food.slot.exception.TimeSlotErrorCode;
import com.sup2i.food.slot.exception.TimeSlotException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice(
    assignableTypes = {
        TimeSlotController.class,
        OrderController.class
    }
)
public class TimeSlotExceptionHandler {

    @ExceptionHandler(
        TimeSlotException.class
    )
    public ResponseEntity<ApiErrorResponse> handle(
        TimeSlotException exception,
        HttpServletRequest request
    ) {

        TimeSlotErrorCode errorCode =
            exception.getErrorCode();

        HttpStatus status =
            switch (errorCode) {

                case SLOT_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;

                case SLOT_CLOSED,
                     SLOT_FULL ->
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
                errorCode.name(),
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