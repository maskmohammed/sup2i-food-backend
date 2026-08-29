package com.sup2i.food.security.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class SafeTextValidator
    implements ConstraintValidator<SafeText, String> {

    private static final Pattern DANGEROUS =
        Pattern.compile(
            "(?i)<\\s*(script|iframe|svg|object|embed|"
                + "style|link|body|meta|base|form)|"
                + "\\son(load|error|click|mouseover|focus|"
                + "change|submit|input|keypress|keyup|keydown)\\s*=|"
                + "\\bjavascript\\s*:|"
                + "\\bdata\\s*:\\s*text/html|"
                + "\\bexpression\\s*\\(|"
                + "\\balert\\s*\\(|"
                + "vbs\\s*:"
        );

    @Override
    public boolean isValid(
        String value,
        ConstraintValidatorContext context
    ) {
        if (value == null || value.isBlank()) {
            return true;
        }

        return !DANGEROUS.matcher(value).find();
    }
}