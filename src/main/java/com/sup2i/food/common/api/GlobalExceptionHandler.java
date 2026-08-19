package com.sup2i.food.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        Map<String, Object> fields =
            new LinkedHashMap<>();

        exception.getBindingResult()
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
            Map.of("fields", fields)
        );
    }

    @ExceptionHandler({
        BadCredentialsException.class,
        CredentialsExpiredException.class
    })
    public ResponseEntity<ApiErrorResponse> unauthorized(
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

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiErrorResponse> disabled(
        DisabledException exception,
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

    private ResponseEntity<ApiErrorResponse> error(
        HttpStatus status,
        String code,
        String message,
        HttpServletRequest request,
        Map<String, Object> details
    ) {
        ApiErrorResponse body =
            new ApiErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                UUID.randomUUID().toString(),
                details
            );

        return ResponseEntity
            .status(status)
            .body(body);
    }
}