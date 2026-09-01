package com.sup2i.food.survey.service;


import com.sup2i.food.survey.api.dto.AddSurveyQuestionCommand;
import com.sup2i.food.survey.api.dto.CreateSurveyCommand;
import com.sup2i.food.survey.api.dto.SubmitSurveyResponseCommand;
import com.sup2i.food.survey.api.dto.SurveyQuestionResponse;
import com.sup2i.food.survey.api.dto.SurveyResponse;
import com.sup2i.food.survey.api.dto.SurveySubmissionResponse;
import com.sup2i.food.survey.domain.SurveyQuestionType;
import com.sup2i.food.survey.domain.SurveyStatus;
import com.sup2i.food.survey.exception.SurveyConflictException;
import com.sup2i.food.survey.exception.SurveyNotFoundException;
import com.sup2i.food.survey.exception.SurveyValidationException;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SurveyService {

    private static final int TITLE_MAX_LENGTH = 180;
    private static final int MAX_LIST_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;

    public SurveyService(
        JdbcTemplate jdbcTemplate
    ) {

        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public SurveyResponse createDraft(
        UUID organizationId,
        UUID surveyId,
        UUID createdByUserId,
        CreateSurveyCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            surveyId,
            "Survey id"
        );

        requireId(
            createdByUserId,
            "Created-by user id"
        );

        validateCreateCommand(
            command
        );

        String title =
            requiredText(
                command.title(),
                "Survey title",
                TITLE_MAX_LENGTH
            );

        String description =
            nullableText(
                command.description()
            );

        lockUser(
            organizationId,
            createdByUserId
        );

        SurveyResponse existing =
            findSurvey(
                organizationId,
                surveyId,
                false
            );

        if (existing != null) {

            boolean same =
                existing.status()
                    == SurveyStatus.DRAFT
                    && existing.createdBy().equals(
                        createdByUserId
                    )
                    && existing.title().equals(
                        title
                    )
                    && Objects.equals(
                        existing.description(),
                        description
                    )
                    && samePostgresTimestamp(
                        existing.startsAt(),
                        command.startsAt()
                    )
                    && samePostgresTimestamp(
                        existing.endsAt(),
                        command.endsAt()
                    );

            if (same) {
                return replay(
                    existing
                );
            }

            throw new SurveyConflictException(
                "Survey identifier is already used by another payload."
            );
        }

        try {

            jdbcTemplate.update(
                """
                INSERT INTO surveys(
                    id,
                    organization_id,
                    title,
                    description,
                    status,
                    starts_at,
                    ends_at,
                    created_by
                )
                VALUES(
                    ?, ?, ?, ?,
                    'DRAFT',
                    ?, ?, ?
                )
                """,
                surveyId,
                organizationId,
                title,
                description,
                command.startsAt(),
                command.endsAt(),
                createdByUserId
            );

        } catch (DataIntegrityViolationException exception) {

            throw new SurveyConflictException(
                "Survey conflicts with an existing database resource."
            );
        }

        return get(
            organizationId,
            surveyId
        );
    }

    @Transactional(readOnly = true)
    public SurveyResponse get(
        UUID organizationId,
        UUID surveyId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            surveyId,
            "Survey id"
        );

        SurveyResponse response =
            findSurvey(
                organizationId,
                surveyId,
                false
            );

        if (response == null) {

            throw new SurveyNotFoundException(
                "Survey does not exist."
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<SurveyResponse> list(
        UUID organizationId,
        int limit
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        int safeLimit =
            safeLimit(
                limit
            );

        return jdbcTemplate.query(
            """
            SELECT
                id,
                organization_id,
                title,
                description,
                status,
                starts_at,
                ends_at,
                created_by,
                created_at,
                updated_at
            FROM surveys
            WHERE organization_id = ?
            ORDER BY created_at DESC, id
            LIMIT ?
            """,
            (rs, rowNum) ->
                mapSurvey(
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                    rs.getObject(
                        "organization_id",
                        UUID.class
                    ),
                    rs.getString(
                        "title"
                    ),
                    rs.getString(
                        "description"
                    ),
                    SurveyStatus.valueOf(
                        rs.getString(
                            "status"
                        )
                    ),
                    rs.getObject(
                        "starts_at",
                        OffsetDateTime.class
                    ),
                    rs.getObject(
                        "ends_at",
                        OffsetDateTime.class
                    ),
                    rs.getObject(
                        "created_by",
                        UUID.class
                    ),
                    rs.getObject(
                        "created_at",
                        OffsetDateTime.class
                    ),
                    rs.getObject(
                        "updated_at",
                        OffsetDateTime.class
                    ),
                    false
                ),
            organizationId,
            safeLimit
        );
    }

    @Transactional
    public SurveyQuestionResponse addQuestion(
        UUID organizationId,
        UUID surveyId,
        UUID questionId,
        AddSurveyQuestionCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            surveyId,
            "Survey id"
        );

        requireId(
            questionId,
            "Question id"
        );

        validateQuestionCommand(
            command
        );

        SurveyResponse survey =
            lockSurvey(
                organizationId,
                surveyId
            );

        if (
            survey.status()
                != SurveyStatus.DRAFT
        ) {

            throw new SurveyConflictException(
                "Questions can only be modified while the survey is DRAFT."
            );
        }

        String question =
            requiredText(
                command.question(),
                "Survey question",
                Integer.MAX_VALUE
            );

        String optionsJson =
            canonicalNullableJson(
                command.optionsJson(),
                "Survey question options"
            );

        SurveyQuestionResponse existing =
            findQuestion(
                organizationId,
                surveyId,
                questionId
            );

        if (existing != null) {

            boolean same =
                existing.question().equals(
                    question
                )
                    && existing.type()
                        == command.type()
                    && sameJson(
                        existing.optionsJson(),
                        optionsJson
                    )
                    && existing.displayOrder()
                        == command.displayOrder()
                    && existing.required()
                        == command.required();

            if (same) {
                return replay(
                    existing
                );
            }

            throw new SurveyConflictException(
                "Survey question identifier is already used by another payload."
            );
        }

        try {

            jdbcTemplate.update(
                """
                INSERT INTO survey_questions(
                    id,
                    survey_id,
                    question,
                    type,
                    options,
                    display_order,
                    required
                )
                VALUES(
                    ?, ?, ?, ?,
                    CAST(? AS JSONB),
                    ?, ?
                )
                """,
                questionId,
                surveyId,
                question,
                command.type().name(),
                optionsJson,
                command.displayOrder(),
                command.required()
            );

        } catch (DataIntegrityViolationException exception) {

            throw new SurveyConflictException(
                "Survey question conflicts with an existing database resource."
            );
        }

        return getQuestion(
            organizationId,
            surveyId,
            questionId
        );
    }

    @Transactional(readOnly = true)
    public SurveyQuestionResponse getQuestion(
        UUID organizationId,
        UUID surveyId,
        UUID questionId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            surveyId,
            "Survey id"
        );

        requireId(
            questionId,
            "Question id"
        );

        SurveyQuestionResponse response =
            findQuestion(
                organizationId,
                surveyId,
                questionId
            );

        if (response == null) {

            throw new SurveyNotFoundException(
                "Survey question does not exist."
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<SurveyQuestionResponse> listQuestions(
        UUID organizationId,
        UUID surveyId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            surveyId,
            "Survey id"
        );

        requireSurvey(
            organizationId,
            surveyId
        );

        return jdbcTemplate.query(
            """
            SELECT
                q.id,
                q.survey_id,
                q.question,
                q.type,
                q.options::text AS options_json,
                q.display_order,
                q.required
            FROM survey_questions q
            JOIN surveys s
              ON s.id = q.survey_id
            WHERE q.survey_id = ?
              AND s.organization_id = ?
            ORDER BY q.display_order, q.id
            """,
            (rs, rowNum) ->
                new SurveyQuestionResponse(
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                    rs.getObject(
                        "survey_id",
                        UUID.class
                    ),
                    rs.getString(
                        "question"
                    ),
                    SurveyQuestionType.valueOf(
                        rs.getString(
                            "type"
                        )
                    ),
                    rs.getString(
                        "options_json"
                    ),
                    rs.getInt(
                        "display_order"
                    ),
                    rs.getBoolean(
                        "required"
                    ),
                    false
                ),
            surveyId,
            organizationId
        );
    }

    @Transactional
    public SurveyResponse activate(
        UUID organizationId,
        UUID surveyId
    ) {

        return transition(
            organizationId,
            surveyId,
            SurveyStatus.DRAFT,
            SurveyStatus.ACTIVE
        );
    }

    @Transactional
    public SurveyResponse close(
        UUID organizationId,
        UUID surveyId
    ) {

        return transition(
            organizationId,
            surveyId,
            SurveyStatus.ACTIVE,
            SurveyStatus.CLOSED
        );
    }

    @Transactional
    public SurveyResponse archive(
        UUID organizationId,
        UUID surveyId
    ) {

        return transition(
            organizationId,
            surveyId,
            SurveyStatus.CLOSED,
            SurveyStatus.ARCHIVED
        );
    }

    @Transactional
    public SurveySubmissionResponse submit(
        UUID organizationId,
        UUID responseId,
        UUID surveyId,
        UUID studentId,
        SubmitSurveyResponseCommand command
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            responseId,
            "Survey response id"
        );

        requireId(
            surveyId,
            "Survey id"
        );

        requireId(
            studentId,
            "Student id"
        );

        if (command == null) {

            throw new SurveyValidationException(
                "Survey response command is required."
            );
        }

        String answersJson =
            canonicalRequiredJson(
                command.answersJson(),
                "Survey answers"
            );

        SurveyResponse survey =
            lockSurvey(
                organizationId,
                surveyId
            );

        if (
            survey.status()
                != SurveyStatus.ACTIVE
        ) {

            throw new SurveyConflictException(
                "Survey responses are accepted only while the survey is ACTIVE."
            );
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        if (
            survey.startsAt() != null
            && now.isBefore(
                survey.startsAt()
            )
        ) {

            throw new SurveyConflictException(
                "Survey response window has not started."
            );
        }

        if (
            survey.endsAt() != null
            && !now.isBefore(
                survey.endsAt()
            )
        ) {

            throw new SurveyConflictException(
                "Survey response window is closed."
            );
        }

        lockStudent(
            organizationId,
            studentId
        );

        SurveySubmissionResponse byId =
            findSubmissionById(
                organizationId,
                responseId
            );

        if (byId != null) {

            boolean same =
                byId.surveyId().equals(
                    surveyId
                )
                    && byId.studentId().equals(
                        studentId
                    )
                    && sameJson(
                        byId.answersJson(),
                        answersJson
                    );

            if (same) {
                return replay(
                    byId
                );
            }

            throw new SurveyConflictException(
                "Survey response identifier is already used by another payload."
            );
        }

        SurveySubmissionResponse existing =
            findSubmissionForStudent(
                organizationId,
                surveyId,
                studentId
            );

        if (existing != null) {

            throw new SurveyConflictException(
                "Student already submitted a response for this survey."
            );
        }

        try {

            jdbcTemplate.update(
                """
                INSERT INTO survey_responses(
                    id,
                    survey_id,
                    student_id,
                    answers
                )
                VALUES(
                    ?, ?, ?,
                    CAST(? AS JSONB)
                )
                """,
                responseId,
                surveyId,
                studentId,
                answersJson
            );

        } catch (DataIntegrityViolationException exception) {

            throw new SurveyConflictException(
                "Survey response conflicts with an existing database resource."
            );
        }

        return getSubmission(
            organizationId,
            responseId
        );
    }

    @Transactional(readOnly = true)
    public SurveySubmissionResponse getSubmission(
        UUID organizationId,
        UUID responseId
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            responseId,
            "Survey response id"
        );

        SurveySubmissionResponse response =
            findSubmissionById(
                organizationId,
                responseId
            );

        if (response == null) {

            throw new SurveyNotFoundException(
                "Survey response does not exist."
            );
        }

        return response;
    }

    private SurveyResponse transition(
        UUID organizationId,
        UUID surveyId,
        SurveyStatus expectedStatus,
        SurveyStatus targetStatus
    ) {

        requireId(
            organizationId,
            "Organization id"
        );

        requireId(
            surveyId,
            "Survey id"
        );

        SurveyResponse current =
            lockSurvey(
                organizationId,
                surveyId
            );

        if (
            current.status()
                == targetStatus
        ) {
            return replay(
                current
            );
        }

        if (
            current.status()
                != expectedStatus
        ) {

            throw new SurveyConflictException(
                "Survey cannot transition from "
                    + current.status()
                    + " to "
                    + targetStatus
                    + "."
            );
        }

        int changed =
            jdbcTemplate.update(
                """
                UPDATE surveys
                SET status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND organization_id = ?
                  AND status = ?
                """,
                targetStatus.name(),
                surveyId,
                organizationId,
                expectedStatus.name()
            );

        if (changed != 1) {

            throw new SurveyConflictException(
                "Survey state changed concurrently."
            );
        }

        return get(
            organizationId,
            surveyId
        );
    }

    private SurveyResponse lockSurvey(
        UUID organizationId,
        UUID surveyId
    ) {

        SurveyResponse response =
            findSurvey(
                organizationId,
                surveyId,
                true
            );

        if (response == null) {

            throw new SurveyNotFoundException(
                "Survey does not exist."
            );
        }

        return response;
    }

    private void requireSurvey(
        UUID organizationId,
        UUID surveyId
    ) {

        SurveyResponse response =
            findSurvey(
                organizationId,
                surveyId,
                false
            );

        if (response == null) {

            throw new SurveyNotFoundException(
                "Survey does not exist."
            );
        }
    }

    private SurveyResponse findSurvey(
        UUID organizationId,
        UUID surveyId,
        boolean forUpdate
    ) {

        String lockClause =
            forUpdate
                ? " FOR UPDATE"
                : "";

        String sql =
            """
            SELECT
                id,
                organization_id,
                title,
                description,
                status,
                starts_at,
                ends_at,
                created_by,
                created_at,
                updated_at
            FROM surveys
            WHERE id = ?
              AND organization_id = ?
            """
                + lockClause;

        List<SurveyResponse> rows =
            jdbcTemplate.query(
                sql,
                (rs, rowNum) ->
                    mapSurvey(
                        rs.getObject(
                            "id",
                            UUID.class
                        ),
                        rs.getObject(
                            "organization_id",
                            UUID.class
                        ),
                        rs.getString(
                            "title"
                        ),
                        rs.getString(
                            "description"
                        ),
                        SurveyStatus.valueOf(
                            rs.getString(
                                "status"
                            )
                        ),
                        rs.getObject(
                            "starts_at",
                            OffsetDateTime.class
                        ),
                        rs.getObject(
                            "ends_at",
                            OffsetDateTime.class
                        ),
                        rs.getObject(
                            "created_by",
                            UUID.class
                        ),
                        rs.getObject(
                            "created_at",
                            OffsetDateTime.class
                        ),
                        rs.getObject(
                            "updated_at",
                            OffsetDateTime.class
                        ),
                        false
                    ),
                surveyId,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.getFirst();
    }

    private SurveyQuestionResponse findQuestion(
        UUID organizationId,
        UUID surveyId,
        UUID questionId
    ) {

        List<SurveyQuestionResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    q.id,
                    q.survey_id,
                    q.question,
                    q.type,
                    q.options::text AS options_json,
                    q.display_order,
                    q.required
                FROM survey_questions q
                JOIN surveys s
                  ON s.id = q.survey_id
                WHERE q.id = ?
                  AND q.survey_id = ?
                  AND s.organization_id = ?
                """,
                (rs, rowNum) ->
                    new SurveyQuestionResponse(
                        rs.getObject(
                            "id",
                            UUID.class
                        ),
                        rs.getObject(
                            "survey_id",
                            UUID.class
                        ),
                        rs.getString(
                            "question"
                        ),
                        SurveyQuestionType.valueOf(
                            rs.getString(
                                "type"
                            )
                        ),
                        rs.getString(
                            "options_json"
                        ),
                        rs.getInt(
                            "display_order"
                        ),
                        rs.getBoolean(
                            "required"
                        ),
                        false
                    ),
                questionId,
                surveyId,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.getFirst();
    }

    private SurveySubmissionResponse findSubmissionById(
        UUID organizationId,
        UUID responseId
    ) {

        List<SurveySubmissionResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    sr.id,
                    sr.survey_id,
                    sr.student_id,
                    sr.answers::text AS answers_json,
                    sr.submitted_at
                FROM survey_responses sr
                JOIN surveys s
                  ON s.id = sr.survey_id
                WHERE sr.id = ?
                  AND s.organization_id = ?
                """,
                (rs, rowNum) ->
                    new SurveySubmissionResponse(
                        rs.getObject(
                            "id",
                            UUID.class
                        ),
                        rs.getObject(
                            "survey_id",
                            UUID.class
                        ),
                        rs.getObject(
                            "student_id",
                            UUID.class
                        ),
                        rs.getString(
                            "answers_json"
                        ),
                        rs.getObject(
                            "submitted_at",
                            OffsetDateTime.class
                        ),
                        false
                    ),
                responseId,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.getFirst();
    }

    private SurveySubmissionResponse findSubmissionForStudent(
        UUID organizationId,
        UUID surveyId,
        UUID studentId
    ) {

        List<SurveySubmissionResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    sr.id,
                    sr.survey_id,
                    sr.student_id,
                    sr.answers::text AS answers_json,
                    sr.submitted_at
                FROM survey_responses sr
                JOIN surveys s
                  ON s.id = sr.survey_id
                WHERE sr.survey_id = ?
                  AND sr.student_id = ?
                  AND s.organization_id = ?
                """,
                (rs, rowNum) ->
                    new SurveySubmissionResponse(
                        rs.getObject(
                            "id",
                            UUID.class
                        ),
                        rs.getObject(
                            "survey_id",
                            UUID.class
                        ),
                        rs.getObject(
                            "student_id",
                            UUID.class
                        ),
                        rs.getString(
                            "answers_json"
                        ),
                        rs.getObject(
                            "submitted_at",
                            OffsetDateTime.class
                        ),
                        false
                    ),
                surveyId,
                studentId,
                organizationId
            );

        if (rows.isEmpty()) {
            return null;
        }

        return rows.getFirst();
    }

    private SurveyResponse mapSurvey(
        UUID id,
        UUID organizationId,
        String title,
        String description,
        SurveyStatus status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        boolean replayed
    ) {

        return new SurveyResponse(
            id,
            organizationId,
            title,
            description,
            status,
            startsAt,
            endsAt,
            createdBy,
            createdAt,
            updatedAt,
            replayed
        );
    }

    private SurveyResponse replay(
        SurveyResponse response
    ) {

        return new SurveyResponse(
            response.id(),
            response.organizationId(),
            response.title(),
            response.description(),
            response.status(),
            response.startsAt(),
            response.endsAt(),
            response.createdBy(),
            response.createdAt(),
            response.updatedAt(),
            true
        );
    }

    private SurveyQuestionResponse replay(
        SurveyQuestionResponse response
    ) {

        return new SurveyQuestionResponse(
            response.id(),
            response.surveyId(),
            response.question(),
            response.type(),
            response.optionsJson(),
            response.displayOrder(),
            response.required(),
            true
        );
    }

    private SurveySubmissionResponse replay(
        SurveySubmissionResponse response
    ) {

        return new SurveySubmissionResponse(
            response.id(),
            response.surveyId(),
            response.studentId(),
            response.answersJson(),
            response.submittedAt(),
            true
        );
    }

    private void validateCreateCommand(
        CreateSurveyCommand command
    ) {

        if (command == null) {

            throw new SurveyValidationException(
                "Survey command is required."
            );
        }

        requiredText(
            command.title(),
            "Survey title",
            TITLE_MAX_LENGTH
        );

        if (
            command.startsAt() != null
            && command.endsAt() != null
            && !command.endsAt().isAfter(
                command.startsAt()
            )
        ) {

            throw new SurveyValidationException(
                "Survey end must be after survey start."
            );
        }
    }

    private void validateQuestionCommand(
        AddSurveyQuestionCommand command
    ) {

        if (command == null) {

            throw new SurveyValidationException(
                "Survey question command is required."
            );
        }

        requiredText(
            command.question(),
            "Survey question",
            Integer.MAX_VALUE
        );

        if (command.type() == null) {

            throw new SurveyValidationException(
                "Survey question type is required."
            );
        }

        if (command.displayOrder() < 0) {

            throw new SurveyValidationException(
                "Survey question display order must be non-negative."
            );
        }
    }

    private void lockUser(
        UUID organizationId,
        UUID userId
    ) {

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT id
                FROM users
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                userId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new SurveyNotFoundException(
                "User does not exist in this organization."
            );
        }
    }

    private void lockStudent(
        UUID organizationId,
        UUID studentId
    ) {

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT s.id
                FROM students s
                JOIN users u
                  ON u.id = s.user_id
                WHERE s.id = ?
                  AND u.organization_id = ?
                FOR UPDATE OF s
                """,
                (rs, rowNum) ->
                    rs.getObject(
                        "id",
                        UUID.class
                    ),
                studentId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new SurveyNotFoundException(
                "Student does not exist in this organization."
            );
        }
    }

    private boolean samePostgresTimestamp(
        OffsetDateTime left,
        OffsetDateTime right
    ) {

        if (
            left == null
            || right == null
        ) {
            return left == right;
        }

        Duration difference =
            Duration.between(
                left.toInstant(),
                right.toInstant()
            )
                .abs();

        Duration databasePrecision =
            Duration.ofNanos(
                1_000L
            );

        return difference.compareTo(
            databasePrecision
        ) < 0;
    }

    private boolean sameJson(
        String left,
        String right
    ) {

        if (
            left == null
            || right == null
        ) {
            return left == right;
        }

        try {

            Boolean equal =
                jdbcTemplate.queryForObject(
                    """
                    SELECT
                        CAST(? AS JSONB)
                        =
                        CAST(? AS JSONB)
                    """,
                    Boolean.class,
                    left,
                    right
                );

            return Boolean.TRUE.equals(
                equal
            );

        } catch (DataAccessException exception) {

            throw new SurveyConflictException(
                "Stored survey JSON is invalid."
            );
        }
    }

    private String canonicalRequiredJson(
        String value,
        String label
    ) {

        if (
            value == null
            || value.isBlank()
        ) {

            throw new SurveyValidationException(
                label + " is required."
            );
        }

        return canonicalJson(
            value,
            label
        );
    }

    private String canonicalNullableJson(
        String value,
        String label
    ) {

        if (
            value == null
            || value.isBlank()
        ) {
            return null;
        }

        return canonicalJson(
            value,
            label
        );
    }

    private String canonicalJson(
        String value,
        String label
    ) {

        try {

            String canonical =
                jdbcTemplate.queryForObject(
                    """
                    SELECT CAST(? AS JSONB)::text
                    """,
                    String.class,
                    value
                );

            if (canonical == null) {

                throw new SurveyValidationException(
                    label + " must contain valid JSON."
                );
            }

            return canonical;

        } catch (DataAccessException exception) {

            throw new SurveyValidationException(
                label + " must contain valid JSON."
            );
        }
    }

    private String requiredText(
        String value,
        String label,
        int maxLength
    ) {

        if (value == null) {

            throw new SurveyValidationException(
                label + " is required."
            );
        }

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {

            throw new SurveyValidationException(
                label + " is required."
            );
        }

        if (
            maxLength != Integer.MAX_VALUE
            && trimmed.length() > maxLength
        ) {

            throw new SurveyValidationException(
                label + " is too long."
            );
        }

        return trimmed;
    }

    private String nullableText(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed;
    }

    private int safeLimit(
        int value
    ) {

        if (value < 1) {
            return 50;
        }

        return Math.min(
            value,
            MAX_LIST_LIMIT
        );
    }

    private void requireId(
        UUID value,
        String label
    ) {

        if (value == null) {

            throw new SurveyValidationException(
                label + " is required."
            );
        }
    }
}