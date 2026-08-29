package com.sup2i.food.security.api;

import com.sup2i.food.security.api.dto.AuthResponse;
import com.sup2i.food.security.api.dto.ForgotPasswordRequest;
import com.sup2i.food.security.api.dto.ForgotPasswordResponse;
import com.sup2i.food.security.api.dto.LoginRequest;
import com.sup2i.food.security.api.dto.MfaTotpConfirmRequest;
import com.sup2i.food.security.api.dto.MfaTotpConfirmResponse;
import com.sup2i.food.security.api.dto.MfaTotpSetupRequest;
import com.sup2i.food.security.api.dto.MfaTotpSetupResponse;
import com.sup2i.food.security.api.dto.RefreshRequest;
import com.sup2i.food.security.api.dto.ResetPasswordRequest;
import com.sup2i.food.security.service.AuthResponseService;
import com.sup2i.food.security.service.AuthenticationTokens;
import com.sup2i.food.security.service.LocalAuthenticationService;
import com.sup2i.food.security.service.MfaEnrollmentService;
import com.sup2i.food.security.service.PasswordResetService;
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
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.UUID;

@Tag(name = "Security", description = "Authentication, MFA, tokens, and the current-user profile.")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LocalAuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;
    private final AuthResponseService responseService;
    private final MfaEnrollmentService mfaEnrollmentService;
    private final PasswordResetService passwordResetService;

    public AuthController(
        LocalAuthenticationService authenticationService,
        RefreshTokenService refreshTokenService,
        AuthResponseService responseService,
        MfaEnrollmentService mfaEnrollmentService,
        PasswordResetService passwordResetService
    ) {
        this.authenticationService = authenticationService;
        this.refreshTokenService = refreshTokenService;
        this.responseService = responseService;
        this.mfaEnrollmentService = mfaEnrollmentService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(
        @Valid
        @RequestBody
        ForgotPasswordRequest request
    ) {

        return passwordResetService.requestReset(
            request.email()
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
        @Valid
        @RequestBody
        ResetPasswordRequest request
    ) {

        passwordResetService.resetPassword(
            request.token(),
            request.newPassword()
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
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
                request.mfaCode(),
                request.recoveryCode(),
                compact(userAgent),
                resolveIp(httpRequest),
                userAgent
            );

        return noStore(
            responseService.create(tokens)
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
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

        return noStore(
            responseService.create(tokens)
        );
    }

    private ResponseEntity<AuthResponse> noStore(
        AuthResponse body
    ) {
        return ResponseEntity
            .ok()
            .header(
                "Cache-Control",
                "no-store"
            )
            .body(body);
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

    @PostMapping("/mfa/totp/setup")
    public ResponseEntity<MfaTotpSetupResponse>
        setupTotp(
            @Valid
            @RequestBody
            MfaTotpSetupRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader(
                value = "User-Agent",
                required = false
            )
            String userAgent
        ) {

        String email =
            request.email()
                .trim()
                .toLowerCase(
                    Locale.ROOT
                );

        MfaEnrollmentService.SetupResult result =
            mfaEnrollmentService.start(
                email,
                request.password(),
                request.label(),
                resolveIp(httpRequest),
                userAgent
            );

        return ResponseEntity
            .ok()
            .header(
                "Cache-Control",
                "no-store"
            )
            .body(
                new MfaTotpSetupResponse(
                    result.methodId(),
                    result.secret(),
                    result.otpauthUri()
                )
            );
    }

    @PostMapping("/mfa/totp/confirm")
    public ResponseEntity<MfaTotpConfirmResponse>
        confirmTotp(
            @Valid
            @RequestBody
            MfaTotpConfirmRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader(
                value = "User-Agent",
                required = false
            )
            String userAgent
        ) {

        String email =
            request.email()
                .trim()
                .toLowerCase(
                    Locale.ROOT
                );

        MfaEnrollmentService.ConfirmationResult result =
            mfaEnrollmentService.confirm(
                email,
                request.password(),
                request.methodId(),
                request.code(),
                compact(userAgent),
                resolveIp(httpRequest),
                userAgent
            );

        AuthResponse auth =
            responseService.create(
                result.tokens()
            );

        return ResponseEntity
            .ok()
            .header(
                "Cache-Control",
                "no-store"
            )
            .body(
                new MfaTotpConfirmResponse(
                    auth,
                    result.recoveryCodes()
                )
            );
    }


}