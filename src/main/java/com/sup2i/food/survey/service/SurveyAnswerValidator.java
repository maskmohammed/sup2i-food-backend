package com.sup2i.food.survey.service;

import com.sup2i.food.survey.domain.QuestionType;
import com.sup2i.food.survey.domain.SurveyQuestion;
import com.sup2i.food.survey.exception.SurveyValidationException;

import java.util.List;

/**
 * Pure answer validator, unit tested without a database.
 */
public final class SurveyAnswerValidator {

    private static final int MAX_TEXT_LENGTH = 2000;

    private SurveyAnswerValidator() {
    }

    public static void validate(
        SurveyQuestion question,
        Object answer
    ) {
        if (
            question.isRequired()
                && (answer == null || isEmpty(answer))
        ) {
            throw new SurveyValidationException(
                "Question \"" + question.getQuestion()
                    + "\" is required."
            );
        }

        if (answer == null || isEmpty(answer)) {
            return;
        }

        switch (question.getType()) {
            case RATING -> validateRating(
                question,
                answer
            );
            case TEXT -> validateText(
                question,
                answer
            );
            case SINGLE_CHOICE -> validateChoice(
                question,
                answer,
                false
            );
            case MULTI_CHOICE -> validateChoice(
                question,
                answer,
                true
            );
        }
    }

    private static void validateRating(
        SurveyQuestion question,
        Object answer
    ) {
        if (!(answer instanceof Number number)) {
            throw invalid(question);
        }

        int value =
            number.intValue();

        if (
            value < 1
                || value > 5
                || number.doubleValue() != value
        ) {
            throw new SurveyValidationException(
                "Question \"" + question.getQuestion()
                    + "\" expects a rating between 1 and 5."
            );
        }
    }

    private static void validateText(
        SurveyQuestion question,
        Object answer
    ) {
        if (!(answer instanceof String text)) {
            throw invalid(question);
        }

        if (
            text.length() > MAX_TEXT_LENGTH
        ) {
            throw new SurveyValidationException(
                "Question \"" + question.getQuestion()
                    + "\" answer is too long."
            );
        }
    }

    private static void validateChoice(
        SurveyQuestion question,
        Object answer,
        boolean multiple
    ) {
        List<String> allowed =
            question.optionValues();

        if (
            allowed.isEmpty()
        ) {
            throw new SurveyValidationException(
                "Question \"" + question.getQuestion()
                    + "\" has no selectable options."
            );
        }

        if (multiple) {
            if (!(answer instanceof List<?> selected)) {
                throw invalid(question);
            }

            if (
                selected.isEmpty()
            ) {
                throw new SurveyValidationException(
                    "Question \"" + question.getQuestion()
                        + "\" requires at least one selection."
                );
            }

            for (Object value : selected) {
                assertInOptions(
                    question,
                    value,
                    allowed
                );
            }
            return;
        }

        assertInOptions(
            question,
            answer,
            allowed
        );
    }

    private static void assertInOptions(
        SurveyQuestion question,
        Object value,
        List<String> allowed
    ) {
        if (
            value instanceof String text
                && allowed.contains(text)
        ) {
            return;
        }

        throw new SurveyValidationException(
            "Question \"" + question.getQuestion()
                + "\" received an invalid option."
        );
    }

    private static boolean isEmpty(Object answer) {
        if (answer instanceof String text) {
            return text.isBlank();
        }
        if (answer instanceof List<?> list) {
            return list.isEmpty();
        }
        return false;
    }

    private static SurveyValidationException invalid(
        SurveyQuestion question
    ) {
        return new SurveyValidationException(
            "Question \"" + question.getQuestion()
                + "\" received an invalid answer."
        );
    }
}