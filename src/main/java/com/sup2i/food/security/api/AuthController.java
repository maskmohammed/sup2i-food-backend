package com.sup2i.food.security.api;

import com.sup2i.food.security.api.dto.AuthResponse;
import com.sup2i.food.security.api.dto.LoginRequest;
import com.sup2i.food.security.api.dto.RefreshRequest;
import com.sup2i.food.security.service.AuthResponseService;
import com.sup2i.food.security.service.AuthenticationTokens;
import com.sup2i.food.security.service.LocalAuthenticationService;
import com.sup2i.food.security.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LocalAuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;
    private final AuthResponseService responseService;

    public AuthController(
        LocalAuthenticationService authenticationService,
        RefreshTokenService refreshTokenService,
        AuthResponseService responseService
    ) {
        this.authenticationService = authenticationService;
        this.refreshTokenService = refreshTokenService;
        this.responseService = responseService;
    }

    @PostMapping("/login")
    public AuthResponse login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        @RequestHeader(
            value = "User-Agent",
            required = false
        ) String userAgent
    ) {
        String email = request.email()
            .trim()
            .toLowerCase(Locale.ROOT);

        AuthenticationTokens tokens =
            authenticationService.login(
                email,
                request.password(),
                compact(userAgent),
                resolveIp(httpRequest),
                userAgent
            );

        return responseService.create(tokens);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(
        @Valid @RequestBody RefreshRequest request,
        HttpServletRequest httpRequest,
        @RequestHeader(
            value = "User-Agent",
            required = false
        ) String userAgent
    ) {
        AuthenticationTokens tokens =
            refreshTokenService.rotate(
                request.refreshToken(),
                compact(userAgent),
                resolveIp(httpRequest)
            );

        return responseService.create(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        JwtAuthenticationToken authentication
    ) {
        String subject =
            authentication.getToken().getSubject();

        String sessionId =
            authentication.getToken()
                .getClaimAsString("sid");

        try {
            refreshTokenService.revokeSession(
                UUID.fromString(sessionId),
                UUID.fromString(subject)
            );
        } catch (
            NullPointerException
            | IllegalArgumentException exception
        ) {
            throw new BadCredentialsException(
                "Invalid authenticated session."
            );
        }

        return ResponseEntity.noContent().build();
    }

    private InetAddress resolveIp(
        HttpServletRequest request
    ) {
        try {
            return InetAddress.getByName(
                request.getRemoteAddr()
            );
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private String compact(String value) {
        if (value == null) {
            return null;
        }

        return value.length() <= 255
            ? value
            : value.substring(0, 255);
    }
}