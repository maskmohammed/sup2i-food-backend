package com.sup2i.food.security.validation;

import com.sup2i.food.security.service.PasswordPolicyService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StrongPasswordValidator
    implements ConstraintValidator<StrongPassword, String> {

    private final PasswordPolicyService passwordPolicyService;

    public StrongPasswordValidator(
        PasswordPolicyService passwordPolicyService
    ) {
        this.passwordPolicyService =
            passwordPolicyService;
    }

    @Override
    public boolean isValid(
        String value,
        ConstraintValidatorContext context
    ) {
        if (!passwordPolicyService.isEnabled()) {
            return true;
        }

        if (value == null) {
            return true;
        }

        List<String> violations =
            passwordPolicyService.violations(value);

        if (violations.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        context.buildConstraintViolationWithTemplate(
                "must satisfy: "
                    + String.join(
                        ", ",
                        violations
                    )
            )
            .addConstraintViolation();

        return false;
    }
}