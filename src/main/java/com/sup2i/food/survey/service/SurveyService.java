package com.sup2i.food.survey.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.StudentRepository;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.repository.OrderRepository;
import com.sup2i.food.subscription.domain.SubscriptionStatus;
import com.sup2i.food.subscription.repository.SubscriptionRepository;
import com.sup2i.food.survey.api.dto.SurveyMutationRequest;
import com.sup2i.food.survey.api.dto.SurveyQuestionRequest;
import com.sup2i.food.survey.api.dto.SurveyQuestionResponse;
import com.sup2i.food.survey.api.dto.SurveyResponse;
import com.sup2i.food.survey.api.dto.SurveyResultResponse;
import com.sup2i.food.survey.api.dto.SurveySubmissionResponse;
import com.sup2i.food.survey.api.dto.SubmitSurveyRequest;
import com.sup2i.food.survey.domain.QuestionType;
import com.sup2i.food.survey.domain.Survey;
import com.sup2i.food.survey.domain.SurveyQuestion;
import com.sup2i.food.survey.domain.SurveySubmission;
import com.sup2i.food.survey.domain.SurveyStatus;
import com.sup2i.food.survey.domain.SurveyTarget;
import com.sup2i.food.survey.exception.SurveyConflictException;
import com.sup2i.food.survey.exception.SurveyNotFoundException;
import com.sup2i.food.survey.exception.SurveyValidationException;
import com.sup2i.food.survey.repository.SurveyRepository;
import com.sup2i.food.survey.repository.SurveyResponseRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SurveyService {

    private static final ObjectMapper JSON =
        new ObjectMapper();

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final OrderRepository orderRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository surveyResponseRepository;

    public SurveyService(
        UserRepository userRepository,
        StudentRepository studentRepository,
        OrderRepository orderRepository,
        SubscriptionRepository subscriptionRepository,
        SurveyRepository surveyRepository,
        SurveyResponseRepository surveyResponseRepository
    ) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.orderRepository = orderRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.surveyRepository = surveyRepository;
        this.surveyResponseRepository = surveyResponseRepository;
    }

    // =========================================================
    // ADMIN OPERATIONS
    // =========================================================

    @Transactional
    public SurveyResponse create(
        UUID actorId,
        SurveyMutationRequest request
    ) {
        User actor =
            requiredUser(actorId);

        validateWindow(request);

        Survey survey =
            new Survey(
                actor.getOrganization(),
                request.title().trim(),
                request.target(),
                actor
            );

        survey.setDescription(
            trimToNull(request.description())
        );

        survey.setStartsAt(request.startsAt());
        survey.setEndsAt(request.endsAt());

        applyQuestions(
            survey,
            request
        );

        return SurveyResponse.from(
            surveyRepository.save(survey)
        );
    }

    @Transactional
    public SurveyResponse update(
        UUID actorId,
        UUID surveyId,
        SurveyMutationRequest request
    ) {
        User actor =
            requiredUser(actorId);

        Survey survey =
            requiredSurvey(
                surveyId,
                actor
            );

        if (survey.getStatus() != SurveyStatus.DRAFT) {
            throw new SurveyConflictException(
                "Seules les enquêtes en brouillon peuvent être modifiées."
            );
        }

        validateWindow(request);
        survey.setUpdatedBy(actor);
        survey.setTitle(request.title().trim());
        survey.setDescription(
            trimToNull(request.description())
        );
        survey.setTarget(request.target());
        survey.setStartsAt(request.startsAt());
        survey.setEndsAt(request.endsAt());

        survey.clearQuestions();
        applyQuestions(
            survey,
            request
        );

        return SurveyResponse.from(
            surveyRepository.save(survey)
        );
    }

    @Transactional
    public SurveyResponse publish(
        UUID actorId,
        UUID surveyId
    ) {
        User actor =
            requiredUser(actorId);

        Survey survey =
            requiredSurvey(
                surveyId,
                actor
            );

        if (survey.getStatus() != SurveyStatus.DRAFT) {
            throw new SurveyConflictException(
                "Seule une enquête en brouillon peut être publiée."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        if (
            survey.getEndsAt() != null
                && survey.getEndsAt().isBefore(now)
        ) {
            throw new SurveyConflictException(
                "La date de fin est déjà passée."
            );
        }

        survey.setStatus(SurveyStatus.ACTIVE);
        survey.setUpdatedBy(actor);

        return SurveyResponse.from(
            surveyRepository.save(survey)
        );
    }

    @Transactional
    public SurveyResponse close(
        UUID actorId,
        UUID surveyId
    ) {
        User actor =
            requiredUser(actorId);

        Survey survey =
            requiredSurvey(
                surveyId,
                actor
            );

        if (survey.getStatus() != SurveyStatus.ACTIVE) {
            throw new SurveyConflictException(
                "Seule une enquête active peut être clôturée."
            );
        }

        survey.setStatus(SurveyStatus.CLOSED);
        survey.setUpdatedBy(actor);

        return SurveyResponse.from(
            surveyRepository.save(survey)
        );
    }

    @Transactional
    public SurveyResponse archive(
        UUID actorId,
        UUID surveyId
    ) {
        User actor =
            requiredUser(actorId);

        Survey survey =
            requiredSurvey(
                surveyId,
                actor
            );

        if (survey.getStatus() != SurveyStatus.CLOSED) {
            throw new SurveyConflictException(
                "Seule une enquête clôturée peut être archivée."
            );
        }

        survey.setStatus(SurveyStatus.ARCHIVED);
        survey.setUpdatedBy(actor);

        return SurveyResponse.from(
            surveyRepository.save(survey)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<SurveyResponse> list(
        UUID actorId,
        SurveyStatus status,
        int page,
        int size
    ) {
        User actor =
            requiredUser(actorId);

        Page<Survey> surveys =
            status == null
                ? surveyRepository
                    .findAllByOrganization_Id(
                        actor.getOrganization().getId(),
                        PageRequest.of(
                            page,
                            size,
                            Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                            )
                        )
                    )
                : surveyRepository
                    .findAllByOrganization_IdAndStatus(
                        actor.getOrganization().getId(),
                        status,
                        PageRequest.of(
                            page,
                            size,
                            Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                            )
                        )
                    );

        return PageResponse.from(
            surveys.map(SurveyResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public SurveyResponse get(
        UUID actorId,
        UUID surveyId
    ) {
        User actor =
            requiredUser(actorId);

        return SurveyResponse.from(
            requiredSurvey(
                surveyId,
                actor
            )
        );
    }

    @Transactional(readOnly = true)
    public SurveyResultResponse results(
        UUID actorId,
        UUID surveyId
    ) {
        User actor =
            requiredUser(actorId);

        Survey survey =
            requiredSurvey(
                surveyId,
                actor
            );

        List<SurveySubmission> responses =
            surveyResponseRepository
                .findAllBySurvey_Id(surveyId);

        List<SurveyResultResponse.QuestionResult>
            questions =
                new ArrayList<>();

        for (
            SurveyQuestion question
            : survey.getQuestions()
        ) {
            questions.add(
                aggregate(
                    question,
                    responses
                )
            );
        }

        return new SurveyResultResponse(
            survey.getId(),
            survey.getTitle(),
            questions
        );
    }

    // =========================================================
    // STUDENT OPERATIONS
    // =========================================================

    @Transactional(readOnly = true)
    public List<SurveyResponse> activeForStudent(
        UUID actorId
    ) {
        Student student =
            requiredStudent(actorId);

        UUID organizationId =
            student
                .getCampus()
                .getOrganization()
                .getId();

        OffsetDateTime now =
            OffsetDateTime.now();

        List<Survey> surveys =
            surveyRepository
                .findAllByOrganization_IdAndStatus(
                    organizationId,
                    SurveyStatus.ACTIVE
                );

        boolean hasDeliveredOrder =
            orderRepository
                .countByStudent_IdAndStatusIn(
                    student.getId(),
                    List.of(
                        OrderStatus.COLLECTED,
                        OrderStatus.COMPLETED
                    )
                ) > 0;

        boolean hasActiveSubscription =
            !subscriptionRepository
                .findAllByStudent_IdAndStatusIn(
                    student.getId(),
                    List.of(
                        SubscriptionStatus.ACTIVE
                    )
                )
                .isEmpty();

        return surveys
            .stream()
            .filter(survey ->
                isWindowOpen(survey, now)
            )
            .filter(survey ->
                SurveyTargetMatcher.matches(
                    survey.getTarget(),
                    hasDeliveredOrder,
                    hasActiveSubscription
                )
            )
            .sorted(
                Collections
                    .reverseOrder(
                        java.util.Comparator.comparing(
                            Survey::getCreatedAt
                        )
                    )
            )
            .map(SurveyResponse::from)
            .toList();
    }

    @Transactional
    public SurveySubmissionResponse respond(
        UUID actorId,
        UUID surveyId,
        SubmitSurveyRequest request
    ) {
        Student student =
            requiredStudent(actorId);

        UUID organizationId =
            student
                .getCampus()
                .getOrganization()
                .getId();

        Survey survey =
            surveyRepository
                .findByIdAndOrganization_Id(
                    surveyId,
                    organizationId
                )
                .orElseThrow(() ->
                    new SurveyNotFoundException(
                        "Enquête introuvable."
                    )
                );

        if (survey.getStatus() != SurveyStatus.ACTIVE) {
            throw new SurveyConflictException(
                "L'enquête n'est pas ouverte."
            );
        }

        if (!isWindowOpen(survey, OffsetDateTime.now())) {
            throw new SurveyConflictException(
                "L'enquête n'est pas ouverte."
            );
        }

        boolean hasDeliveredOrder =
            orderRepository
                .countByStudent_IdAndStatusIn(
                    student.getId(),
                    List.of(
                        OrderStatus.COLLECTED,
                        OrderStatus.COMPLETED
                    )
                ) > 0;

        boolean hasActiveSubscription =
            !subscriptionRepository
                .findAllByStudent_IdAndStatusIn(
                    student.getId(),
                    List.of(
                        SubscriptionStatus.ACTIVE
                    )
                )
                .isEmpty();

        if (
            !SurveyTargetMatcher.matches(
                survey.getTarget(),
                hasDeliveredOrder,
                hasActiveSubscription
            )
        ) {
            throw new SurveyConflictException(
                "Cette enquête ne vous est pas destinée."
            );
        }

        if (
            surveyResponseRepository
                .existsBySurvey_IdAndStudent_Id(
                    survey.getId(),
                    student.getId()
                )
        ) {
            throw new SurveyConflictException(
                "Vous avez déjà répondu à cette enquête."
            );
        }

        Map<UUID, Object> answers =
            validateAnswers(
                survey,
                request
            );

        SurveySubmission response =
            new SurveySubmission(
                survey,
                student,
                toJson(
                    answers.entrySet()
                        .stream()
                        .collect(
                            java.util.stream.Collectors.toMap(
                                entry ->
                                    entry.getKey().toString(),
                                Map.Entry::getValue,
                                (first, second) -> first,
                                LinkedHashMap::new
                            )
                        )
                )
            );

        try {
            surveyResponseRepository.save(response);
        } catch (DataIntegrityViolationException exception) {
            throw new SurveyConflictException(
                "Vous avez déjà répondu à cette enquête."
            );
        }

        return new SurveySubmissionResponse(
            survey.getId(),
            response.getSubmittedAt()
        );
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private Map<UUID, Object> validateAnswers(
        Survey survey,
        SubmitSurveyRequest request
    ) {
        Map<String, Object> raw =
            request.answers() == null
                ? Map.of()
                : request.answers();

        Map<UUID, SurveyQuestion> byId =
            new LinkedHashMap<>();

        survey.getQuestions()
            .forEach(question ->
                byId.put(
                    question.getId(),
                    question
                )
            );

        Map<UUID, Object> answers =
            new LinkedHashMap<>();

        for (
            var entry
            : raw.entrySet()
        ) {
            UUID questionId;

            try {
                questionId =
                    UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException exception) {
                throw new SurveyValidationException(
                    "Réponse invalide pour une question inconnue."
                );
            }

            SurveyQuestion question =
                byId.get(questionId);

            if (question == null) {
                throw new SurveyValidationException(
                    "Question inconnue dans les réponses."
                );
            }

            SurveyAnswerValidator.validate(
                question,
                entry.getValue()
            );

            answers.put(
                questionId,
                entry.getValue()
            );
        }

        for (
            SurveyQuestion question
            : survey.getQuestions()
        ) {
            if (
                question.isRequired()
                    && !answers.containsKey(question.getId())
            ) {
                SurveyAnswerValidator.validate(
                    question,
                    null
                );
            }
        }

        return answers;
    }

    private SurveyResultResponse.QuestionResult aggregate(
        SurveyQuestion question,
        List<SurveySubmission> responses
    ) {
        Map<String, Integer> counts =
            new LinkedHashMap<>();

        List<String> values =
            new ArrayList<>();

        if (
            question.getType() == QuestionType.SINGLE_CHOICE
                || question.getType() == QuestionType.MULTI_CHOICE
        ) {
            question.optionValues()
                .forEach(option ->
                    counts.put(
                        option,
                        0
                    )
                );
        }

        if (question.getType() == QuestionType.RATING) {
            for (
                int i = 1;
                i <= 5;
                i++
            ) {
                counts.put(
                    String.valueOf(i),
                    0
                );
            }
        }

        for (
            SurveySubmission response
            : responses
        ) {
            Object answer =
                answerValue(
                    response,
                    question.getId()
                );

            if (answer == null) {
                continue;
            }

            switch (question.getType()) {
                case RATING, SINGLE_CHOICE -> {
                    String key =
                        String.valueOf(answer);

                    counts.put(
                        key,
                        counts.getOrDefault(key, 0) + 1
                    );
                }
                case MULTI_CHOICE -> {
                    if (answer instanceof List<?> list) {
                        for (Object item : list) {
                            String key =
                                String.valueOf(item);

                            counts.put(
                                key,
                                counts.getOrDefault(key, 0) + 1
                            );
                        }
                    }
                }
                case TEXT -> values.add(
                    String.valueOf(answer)
                );
            }
        }

        return new SurveyResultResponse.QuestionResult(
            question.getId(),
            question.getQuestion(),
            question.getType(),
            (int) responses.size(),
            counts,
            List.copyOf(values)
        );
    }

    private Object answerValue(
        SurveySubmission response,
        UUID questionId
    ) {
        try {
            JsonNode node =
                JSON
                    .readTree(response.getAnswers());

            if (
                node == null
                    || !node.isObject()
            ) {
                return null;
            }

            return JSON.convertValue(
                node.get(questionId.toString()),
                Object.class
            );
        } catch (Exception exception) {
            return null;
        }
    }

    private void applyQuestions(
        Survey survey,
        SurveyMutationRequest request
    ) {
        var requests =
            request.questions();

        for (
            int index = 0;
            index < requests.size();
            index++
        ) {
            SurveyQuestionRequest source =
                requests.get(index);

            if (
                source.type()
                    == QuestionType.SINGLE_CHOICE
                || source.type()
                    == QuestionType.MULTI_CHOICE
            ) {
                if (
                    source.options() == null
                        || source.options().isEmpty()
                ) {
                    throw new SurveyValidationException(
                        "Les questions à choix requièrent des options."
                    );
                }
            }

            if (
                source.type()
                    == QuestionType.RATING
                || source.type()
                    == QuestionType.TEXT
            ) {
                if (
                    source.options() != null
                        && !source.options().isEmpty()
                ) {
                    throw new SurveyValidationException(
                        "Une question de type "
                            + source.type()
                            + " n'accepte pas d'options."
                    );
                }
            }

            survey.addQuestion(
                new SurveyQuestion(
                    source.question().trim(),
                    source.type(),
                    source.options(),
                    index,
                    source.required()
                )
            );
        }
    }

    private void validateWindow(
        SurveyMutationRequest request
    ) {
        if (
            request.startsAt() != null
                && request.endsAt() != null
                && !request.endsAt().isAfter(request.startsAt())
        ) {
            throw new SurveyValidationException(
                "La date de fin doit être après la date de début."
            );
        }
    }

    private boolean isWindowOpen(
        Survey survey,
        OffsetDateTime now
    ) {
        boolean afterStart =
            survey.getStartsAt() == null
                || !now.isBefore(survey.getStartsAt());

        boolean beforeEnd =
            survey.getEndsAt() == null
                || survey.getEndsAt().isAfter(now);

        return afterStart && beforeEnd;
    }

    private Survey requiredSurvey(
        UUID surveyId,
        User actor
    ) {
        return surveyRepository
            .findByIdAndOrganization_Id(
                surveyId,
                actor.getOrganization().getId()
            )
            .orElseThrow(() ->
                new SurveyNotFoundException(
                    "Enquête introuvable."
                )
            );
    }

    private User requiredUser(
        UUID actorId
    ) {
        return userRepository
            .findById(actorId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user does not exist."
                )
            );
    }

    private Student requiredStudent(
        UUID actorId
    ) {
        return studentRepository
            .findByUserId(actorId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated student does not exist."
                )
            );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception exception) {
            throw new SurveyValidationException(
                "Impossible de sérialiser les réponses."
            );
        }
    }
}