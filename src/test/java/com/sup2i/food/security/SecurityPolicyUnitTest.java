package com.sup2i.food.security;

import com.sup2i.food.security.config.SecurityProperties;
import com.sup2i.food.security.exception.PasswordPolicyViolationException;
import com.sup2i.food.security.ratelimit.RateLimitDecision;
import com.sup2i.food.security.ratelimit.RateLimitService;
import com.sup2i.food.security.service.PasswordPolicyService;
import com.sup2i.food.security.validation.SafeTextValidator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityPolicyUnitTest {

    private static final SecurityProperties.PasswordPolicy POLICY =
        new SecurityProperties.PasswordPolicy(
            true,
            12,
            true,
            true,
            true,
            true,
            List.of(
                "password",
                "12345678",
                "sup2i"
            )
        );

    // =========================================================
    // PASSWORD POLICY
    // =========================================================

    @Test
    void strongPasswordPassesPolicy() {

        PasswordPolicyService service =
            new PasswordPolicyService(
                propertiesWithPolicy(POLICY)
            );

        assertThat(
            service.violations(
                "K3$sécurisé!Fort"
            )
        ).isEmpty();

        service.enforce(
            "K3$sécurisé!Fort"
        );
    }

    @Test
    void tooShortPasswordIsRejected() {

        PasswordPolicyService service =
            new PasswordPolicyService(
                propertiesWithPolicy(POLICY)
            );

        List<String> violations =
            service.violations("Aa1!cc");

        assertThat(violations)
            .contains(
                "at least 12 characters"
            );
    }

    @Test
    void missingComplexityIsRejected() {

        PasswordPolicyService service =
            new PasswordPolicyService(
                propertiesWithPolicy(POLICY)
            );

        List<String> violations =
            service.violations(
                "somewherelongenough"
            );

        assertThat(violations)
            .contains(
                "an uppercase letter",
                "a digit",
                "a special character"
            );
    }

    @Test
    void forbiddenWordsAreRejectedCaseInsensitively() {

        PasswordPolicyService service =
            new PasswordPolicyService(
                propertiesWithPolicy(POLICY)
            );

        assertThat(
            service.violations(
                "Password!2026@Sup"
            )
        ).contains(
            "must not contain a common or forbidden word"
        );
    }

    @Test
    void enforcementThrowsOnViolation() {

        PasswordPolicyService service =
            new PasswordPolicyService(
                propertiesWithPolicy(POLICY)
            );

        assertThatThrownBy(() ->
            service.enforce("weak")
        ).isInstanceOf(
            PasswordPolicyViolationException.class
        );
    }

    // =========================================================
    // SAFE TEXT (XSS)
    // =========================================================

    private final SafeTextValidator safeText =
        new SafeTextValidator();

    @Test
    void safeTextAcceptsLegitimateContent() {
        assertThat(safeText.isValid(null, null))
            .isTrue();
        assertThat(safeText.isValid("  ", null))
            .isTrue();
        assertThat(safeText.isValid(
                "Produit frais de la ferme.",
                null
            ))
            .isTrue();
        assertThat(safeText.isValid("<b>OK</b>", null))
            .isTrue();
    }

    @Test
    void safeTextRejectsScriptTags() {
        assertThat(safeText.isValid(
                "<script>alert('x')</script>",
                null
            ))
            .isFalse();
        assertThat(safeText.isValid(
                "<SCRIPT>alert('x')</SCRIPT>",
                null
            ))
            .isFalse();
        assertThat(safeText.isValid(
                "<iframe src='evil'/>",
                null
            ))
            .isFalse();
    }

    @Test
    void safeTextRejectsEventHandlersAndSchemes() {
        assertThat(safeText.isValid(
                "onload=alert(1)",
                null
            ))
            .isFalse();
        assertThat(safeText.isValid(
                "cliquez javascript:alert(1)",
                null
            ))
            .isFalse();
        assertThat(safeText.isValid(
                "data:text/html;base64,PHNjcmlwdD4=",
                null
            ))
            .isFalse();
        assertThat(safeText.isValid(
                "expression(alert(1))",
                null
            ))
            .isFalse();
        assertThat(safeText.isValid(
                "vbs:msgbox(1)",
                null
            ))
            .isFalse();
    }

    // =========================================================
    // RATE LIMITING (Bucket4j)
    // =========================================================

    @Test
    void rateLimiterExhaustsBudgetThenLimits() {

        RateLimitService service =
            new RateLimitService(
                propertiesWithBuckets(true)
            );

        for (int attempt = 0;
             attempt < 3;
             attempt++) {

            RateLimitDecision decision =
                service.tryConsume(
                    "TEST",
                    "client-1"
                );

            assertThat(decision.allowed())
                .isTrue();
        }

        RateLimitDecision blocked =
            service.tryConsume(
                "TEST",
                "client-1"
            );

        assertThat(blocked.allowed())
            .isFalse();
        assertThat(blocked.retryAfterSeconds())
            .isEqualTo(60L);
    }

    @Test
    void rateLimiterKeepsSeparateKeys() {

        RateLimitService service =
            new RateLimitService(
                propertiesWithBuckets(true)
            );

        for (int attempt = 0;
             attempt < 3;
             attempt++) {

            service.tryConsume(
                "TEST",
                "client-1"
            );
        }

        assertThat(
            service.tryConsume(
                "TEST",
                "client-2"
            ).allowed()
        ).isTrue();
    }

    @Test
    void rateLimiterAllowsUnknownBuckets() {

        RateLimitService service =
            new RateLimitService(
                propertiesWithBuckets(true)
            );

        assertThat(
            service.tryConsume(
                "UNKNOWN_BUCKET",
                "client-1"
            ).allowed()
        ).isTrue();
    }

    @Test
    void rateLimiterCanBeDisabled() {

        RateLimitService service =
            new RateLimitService(
                propertiesWithBuckets(false)
            );

        for (int attempt = 0;
             attempt < 100;
             attempt++) {

            assertThat(
                service.tryConsume(
                    "TEST",
                    "client-1"
                ).allowed()
            ).isTrue();
        }
    }

    private SecurityProperties propertiesWithPolicy(
        SecurityProperties.PasswordPolicy policy
    ) {
        return new SecurityProperties(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            policy
        );
    }

    private SecurityProperties propertiesWithBuckets(
        boolean enabled
    ) {
        return new SecurityProperties(
            null,
            null,
            null,
            null,
            null,
            null,
            new SecurityProperties.RateLimit(
                enabled,
                List.of(
                    new SecurityProperties.RateLimit.Bucket(
                        "TEST",
                        3,
                        Duration.ofMinutes(1),
                        1
                    )
                )
            ),
            null
        );
    }
}