package com.sup2i.food.security.service;

import com.sup2i.food.security.config.SecurityProperties;
import com.sup2i.food.security.exception.PasswordPolicyViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PasswordPolicyService {

    private final SecurityProperties properties;

    public PasswordPolicyService(
        SecurityProperties properties
    ) {
        this.properties = properties;
    }

    public void enforce(String password) {
        List<String> violations =
            violations(password);

        if (!violations.isEmpty()) {
            throw new PasswordPolicyViolationException(
                "Password does not meet the required policy: "
                    + String.join("; ", violations) + "."
            );
        }
    }

    public boolean isEnabled() {
        SecurityProperties.PasswordPolicy policy =
            properties.passwordPolicy();

        return policy != null
            && policy.enabled();
    }

    public List<String> violations(String password) {
        SecurityProperties.PasswordPolicy policy =
            properties.passwordPolicy();

        if (
            policy == null
            || !policy.enabled()
        ) {
            return List.of();
        }

        if (
            password == null
            || password.isEmpty()
        ) {
            return List.of(
                "at least "
                    + minLength(policy)
                    + " characters"
            );
        }

        List<String> violations =
            new ArrayList<>();

        int minLength =
            minLength(policy);

        if (password.length() < minLength) {
            violations.add(
                "at least "
                    + minLength
                    + " characters"
            );
        }

        if (
            policy.requireUpper()
            && !password.chars()
                .anyMatch(
                    Character::isUpperCase
                )
        ) {
            violations.add(
                "an uppercase letter"
            );
        }

        if (
            policy.requireLower()
            && !password.chars()
                .anyMatch(
                    Character::isLowerCase
                )
        ) {
            violations.add(
                "a lowercase letter"
            );
        }

        if (
            policy.requireDigit()
            && !password.chars()
                .anyMatch(
                    Character::isDigit
                )
        ) {
            violations.add(
                "a digit"
            );
        }

        if (
            policy.requireSpecial()
            && password.chars()
                .allMatch(
                    Character::isLetterOrDigit
                )
        ) {
            violations.add(
                "a special character"
            );
        }

        String lower =
            password.toLowerCase(
                Locale.ROOT
            );

        if (
            policy.forbidden() != null
            && policy.forbidden()
                .stream()
                .anyMatch(banned ->
                    banned != null
                        && !banned.isBlank()
                        && lower.contains(
                            banned.toLowerCase(
                                Locale.ROOT
                            )
                        )
                )
        ) {
            violations.add(
                "must not contain a common or forbidden word"
            );
        }

        return violations;
    }

    private int minLength(
        SecurityProperties.PasswordPolicy policy
    ) {
        int configured =
            policy.minLength() == null
                ? 12
                : policy.minLength();

        return Math.max(configured, 1);
    }
}