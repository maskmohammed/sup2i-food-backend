package com.sup2i.food.review;

import com.sup2i.food.review.api.dto.CreateReviewCommand;
import com.sup2i.food.review.api.dto.ReviewResponse;
import com.sup2i.food.review.domain.ReviewTargetType;
import com.sup2i.food.review.exception.ReviewNotFoundException;
import com.sup2i.food.review.exception.ReviewValidationException;
import com.sup2i.food.review.service.ReviewService;

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
import com.sup2i.food.survey.service.SurveyService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@ActiveProfiles("test")
@Testcontainers
class ReviewSurveyE2EIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName(
                "sup2i_food_test"
            );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private SurveyService surveyService;

    private TenantSeed primary;
    private TenantSeed other;

    @BeforeEach
    void seedTenants() {

        primary =
            seedTenant(
                "a"
            );

        other =
            seedTenant(
                "b"
            );
    }

    @Test
    void reviewCreateReplayAndExactlyOneProductTarget() {

        UUID reviewId =
            UUID.randomUUID();

        CreateReviewCommand command =
            new CreateReviewCommand(
                ReviewTargetType.PRODUCT,
                primary.productId(),
                5,
                "  Très bon produit  "
            );

        ReviewResponse created =
            reviewService.create(
                primary.organizationId(),
                reviewId,
                primary.studentId(),
                command
            );

        assertThat(created.replayed())
            .isFalse();

        assertThat(created.targetType())
            .isEqualTo(
                ReviewTargetType.PRODUCT
            );

        assertThat(created.targetId())
            .isEqualTo(
                primary.productId()
            );

        assertThat(created.rating())
            .isEqualTo(
                5
            );

        assertThat(created.comment())
            .isEqualTo(
                "Très bon produit"
            );

        ReviewResponse replay =
            reviewService.create(
                primary.organizationId(),
                reviewId,
                primary.studentId(),
                command
            );

        assertThat(replay.replayed())
            .isTrue();

        Map<String, Object> stored =
            jdbcTemplate.queryForMap(
                """
                SELECT
                    product_id,
                    order_id,
                    menu_id
                FROM reviews
                WHERE id = ?
                """,
                reviewId
            );

        assertThat(stored.get("product_id"))
            .isEqualTo(
                primary.productId()
            );

        assertThat(stored.get("order_id"))
            .isNull();

        assertThat(stored.get("menu_id"))
            .isNull();
    }

    @Test
    void reviewValidationAndCrossTenantTargetAreRejected() {

        assertThatThrownBy(
            () ->
                reviewService.create(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    primary.studentId(),
                    new CreateReviewCommand(
                        ReviewTargetType.PRODUCT,
                        primary.productId(),
                        0,
                        null
                    )
                )
        )
            .isInstanceOf(
                ReviewValidationException.class
            );

        assertThatThrownBy(
            () ->
                reviewService.create(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    primary.studentId(),
                    new CreateReviewCommand(
                        ReviewTargetType.PRODUCT,
                        other.productId(),
                        4,
                        null
                    )
                )
        )
            .isInstanceOf(
                ReviewNotFoundException.class
            );
    }

    @Test
    void reviewReadIsTenantIsolated() {

        UUID reviewId =
            UUID.randomUUID();

        reviewService.create(
            primary.organizationId(),
            reviewId,
            primary.studentId(),
            new CreateReviewCommand(
                ReviewTargetType.PRODUCT,
                primary.productId(),
                4,
                "Tenant"
            )
        );

        ReviewResponse own =
            reviewService.get(
                primary.organizationId(),
                reviewId
            );

        assertThat(own.id())
            .isEqualTo(
                reviewId
            );

        assertThatThrownBy(
            () ->
                reviewService.get(
                    other.organizationId(),
                    reviewId
                )
        )
            .isInstanceOf(
                ReviewNotFoundException.class
            );

        assertThat(
            reviewService.listForStudent(
                primary.organizationId(),
                primary.studentId(),
                20
            )
        )
            .extracting(
                ReviewResponse::id
            )
            .contains(
                reviewId
            );

        assertThat(
            reviewService.listForTarget(
                primary.organizationId(),
                ReviewTargetType.PRODUCT,
                primary.productId(),
                20
            )
        )
            .extracting(
                ReviewResponse::id
            )
            .contains(
                reviewId
            );
    }

    @Test
    void surveyCreateReplaySurvivesPostgresTimestampPrecision() {

        UUID surveyId =
            UUID.randomUUID();

        OffsetDateTime startsAt =
            OffsetDateTime.now()
                .withNano(
                    123456789
                )
                .minusMinutes(
                    5
                );

        OffsetDateTime endsAt =
            startsAt.plusHours(
                1
            );

        CreateSurveyCommand command =
            new CreateSurveyCommand(
                "Satisfaction",
                "Questionnaire étudiant",
                startsAt,
                endsAt
            );

        SurveyResponse created =
            surveyService.createDraft(
                primary.organizationId(),
                surveyId,
                primary.userId(),
                command
            );

        assertThat(created.status())
            .isEqualTo(
                SurveyStatus.DRAFT
            );

        assertThat(created.replayed())
            .isFalse();

        SurveyResponse replay =
            surveyService.createDraft(
                primary.organizationId(),
                surveyId,
                primary.userId(),
                command
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThatThrownBy(
            () ->
                surveyService.get(
                    other.organizationId(),
                    surveyId
                )
        )
            .isInstanceOf(
                SurveyNotFoundException.class
            );
    }

    @Test
    void surveyQuestionsSupportAllTypesAndJsonReplayOnlyInDraft() {

        UUID surveyId =
            createDraft(
                null,
                null
            );

        List<SurveyQuestionType> types =
            List.of(
                SurveyQuestionType.TEXT,
                SurveyQuestionType.SINGLE_CHOICE,
                SurveyQuestionType.MULTIPLE_CHOICE,
                SurveyQuestionType.RATING,
                SurveyQuestionType.BOOLEAN,
                SurveyQuestionType.NUMBER
            );

        UUID choiceQuestionId =
            null;

        AddSurveyQuestionCommand choiceCommand =
            null;

        int order =
            0;

        for (
            SurveyQuestionType type : types
        ) {

            UUID questionId =
                UUID.randomUUID();

            String options =
                null;

            boolean choice =
                type == SurveyQuestionType.SINGLE_CHOICE
                    || type == SurveyQuestionType.MULTIPLE_CHOICE;

            if (choice) {

                options =
                    "[ \"A\", \"B\" ]";
            }

            AddSurveyQuestionCommand command =
                new AddSurveyQuestionCommand(
                    "Question " + type.name(),
                    type,
                    options,
                    order,
                    type == SurveyQuestionType.TEXT
                );

            SurveyQuestionResponse created =
                surveyService.addQuestion(
                    primary.organizationId(),
                    surveyId,
                    questionId,
                    command
                );

            assertThat(created.type())
                .isEqualTo(
                    type
                );

            if (
                type == SurveyQuestionType.SINGLE_CHOICE
            ) {

                choiceQuestionId =
                    questionId;

                choiceCommand =
                    command;
            }

            order++;
        }

        assertThat(
            surveyService.listQuestions(
                primary.organizationId(),
                surveyId
            )
        )
            .hasSize(
                6
            )
            .extracting(
                SurveyQuestionResponse::type
            )
            .containsExactly(
                SurveyQuestionType.TEXT,
                SurveyQuestionType.SINGLE_CHOICE,
                SurveyQuestionType.MULTIPLE_CHOICE,
                SurveyQuestionType.RATING,
                SurveyQuestionType.BOOLEAN,
                SurveyQuestionType.NUMBER
            );

        SurveyQuestionResponse replay =
            surveyService.addQuestion(
                primary.organizationId(),
                surveyId,
                choiceQuestionId,
                choiceCommand
            );

        assertThat(replay.replayed())
            .isTrue();

        surveyService.activate(
            primary.organizationId(),
            surveyId
        );

        assertThatThrownBy(
            () ->
                surveyService.addQuestion(
                    primary.organizationId(),
                    surveyId,
                    UUID.randomUUID(),
                    new AddSurveyQuestionCommand(
                        "Late question",
                        SurveyQuestionType.TEXT,
                        null,
                        99,
                        false
                    )
                )
        )
            .isInstanceOf(
                SurveyConflictException.class
            );
    }

    @Test
    void surveyLifecycleIsLinearAndIdempotent() {

        UUID surveyId =
            createDraft(
                null,
                null
            );

        SurveyResponse active =
            surveyService.activate(
                primary.organizationId(),
                surveyId
            );

        assertThat(active.status())
            .isEqualTo(
                SurveyStatus.ACTIVE
            );

        SurveyResponse activeReplay =
            surveyService.activate(
                primary.organizationId(),
                surveyId
            );

        assertThat(activeReplay.replayed())
            .isTrue();

        SurveyResponse closed =
            surveyService.close(
                primary.organizationId(),
                surveyId
            );

        assertThat(closed.status())
            .isEqualTo(
                SurveyStatus.CLOSED
            );

        SurveyResponse archived =
            surveyService.archive(
                primary.organizationId(),
                surveyId
            );

        assertThat(archived.status())
            .isEqualTo(
                SurveyStatus.ARCHIVED
            );

        assertThatThrownBy(
            () ->
                surveyService.close(
                    primary.organizationId(),
                    surveyId
                )
        )
            .isInstanceOf(
                SurveyConflictException.class
            );
    }

    @Test
    void surveySubmitCanonicalReplayAndUniqueStudentResponse() {

        OffsetDateTime startsAt =
            OffsetDateTime.now()
                .minusMinutes(
                    10
                );

        OffsetDateTime endsAt =
            OffsetDateTime.now()
                .plusHours(
                    1
                );

        UUID surveyId =
            createDraft(
                startsAt,
                endsAt
            );

        surveyService.activate(
            primary.organizationId(),
            surveyId
        );

        UUID responseId =
            UUID.randomUUID();

        SubmitSurveyResponseCommand command =
            new SubmitSurveyResponseCommand(
                """
                {
                  "z": 1,
                  "a": true
                }
                """
            );

        SurveySubmissionResponse created =
            surveyService.submit(
                primary.organizationId(),
                responseId,
                surveyId,
                primary.studentId(),
                command
            );

        assertThat(created.replayed())
            .isFalse();

        SurveySubmissionResponse replay =
            surveyService.submit(
                primary.organizationId(),
                responseId,
                surveyId,
                primary.studentId(),
                command
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThatThrownBy(
            () ->
                surveyService.submit(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    surveyId,
                    primary.studentId(),
                    new SubmitSurveyResponseCommand(
                        "{\"other\":true}"
                    )
                )
        )
            .isInstanceOf(
                SurveyConflictException.class
            );

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM survey_responses
                WHERE survey_id = ?
                  AND student_id = ?
                """,
                Integer.class,
                surveyId,
                primary.studentId()
            );

        assertThat(count)
            .isEqualTo(
                1
            );
    }

    @Test
    void surveySubmissionRequiresActiveDateWindowAndTenant() {

        UUID draftId =
            createDraft(
                null,
                null
            );

        assertThatThrownBy(
            () ->
                surveyService.submit(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    draftId,
                    primary.studentId(),
                    new SubmitSurveyResponseCommand(
                        "{\"value\":1}"
                    )
                )
        )
            .isInstanceOf(
                SurveyConflictException.class
            );

        OffsetDateTime futureStart =
            OffsetDateTime.now()
                .plusHours(
                    2
                );

        UUID futureSurveyId =
            createDraft(
                futureStart,
                futureStart.plusHours(
                    1
                )
            );

        surveyService.activate(
            primary.organizationId(),
            futureSurveyId
        );

        assertThatThrownBy(
            () ->
                surveyService.submit(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    futureSurveyId,
                    primary.studentId(),
                    new SubmitSurveyResponseCommand(
                        "{\"value\":1}"
                    )
                )
        )
            .isInstanceOf(
                SurveyConflictException.class
            );

        UUID activeSurveyId =
            createDraft(
                OffsetDateTime.now()
                    .minusMinutes(
                        5
                    ),
                OffsetDateTime.now()
                    .plusHours(
                        1
                    )
            );

        surveyService.activate(
            primary.organizationId(),
            activeSurveyId
        );

        assertThatThrownBy(
            () ->
                surveyService.submit(
                    primary.organizationId(),
                    UUID.randomUUID(),
                    activeSurveyId,
                    other.studentId(),
                    new SubmitSurveyResponseCommand(
                        "{\"value\":1}"
                    )
                )
        )
            .isInstanceOf(
                SurveyNotFoundException.class
            );
    }

    private UUID createDraft(
        OffsetDateTime startsAt,
        OffsetDateTime endsAt
    ) {

        UUID surveyId =
            UUID.randomUUID();

        surveyService.createDraft(
            primary.organizationId(),
            surveyId,
            primary.userId(),
            new CreateSurveyCommand(
                "Survey " + surveyId,
                null,
                startsAt,
                endsAt
            )
        );

        return surveyId;
    }

    private TenantSeed seedTenant(
        String prefix
    ) {

        String suffix =
            UUID.randomUUID()
                .toString()
                .replace(
                    "-",
                    ""
                )
                .substring(
                    0,
                    10
                );

        UUID organizationId =
            UUID.randomUUID();

        UUID campusId =
            UUID.randomUUID();

        UUID userId =
            UUID.randomUUID();

        UUID studentId =
            UUID.randomUUID();

        UUID categoryId =
            UUID.randomUUID();

        UUID productId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO organizations(
                id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, TRUE)
            """,
            organizationId,
            "B19 Organization " + suffix,
            "B19O" + prefix + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO campuses(
                id,
                organization_id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, ?, TRUE)
            """,
            campusId,
            organizationId,
            "B19 Campus " + suffix,
            "B19C" + prefix + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO users(
                id,
                organization_id,
                email,
                first_name,
                last_name,
                status
            )
            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """,
            userId,
            organizationId,
            "b19-" + prefix + "-" + suffix + "@sup2i.test",
            "B19",
            "User"
        );

        jdbcTemplate.update(
            """
            INSERT INTO students(
                id,
                user_id,
                campus_id,
                student_number,
                enrollment_status
            )
            VALUES (?, ?, ?, ?, 'ACTIVE')
            """,
            studentId,
            userId,
            campusId,
            "B19S" + prefix + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO categories(
                id,
                organization_id,
                name,
                slug,
                display_order,
                is_active
            )
            VALUES (?, ?, ?, ?, 0, TRUE)
            """,
            categoryId,
            organizationId,
            "B19 Category " + suffix,
            "b19-category-" + prefix + "-" + suffix
        );

        jdbcTemplate.update(
            """
            INSERT INTO products(
                id,
                organization_id,
                category_id,
                sku,
                name,
                product_type,
                base_price,
                tax_rate,
                track_stock,
                is_prepared,
                is_active
            )
            VALUES(
                ?, ?, ?, ?, ?,
                'PACKAGED',
                10.00,
                0.00,
                TRUE,
                FALSE,
                TRUE
            )
            """,
            productId,
            organizationId,
            categoryId,
            "B19SKU" + prefix + suffix,
            "B19 Product " + suffix
        );

        return new TenantSeed(
            organizationId,
            campusId,
            userId,
            studentId,
            categoryId,
            productId
        );
    }

    private record TenantSeed(
        UUID organizationId,
        UUID campusId,
        UUID userId,
        UUID studentId,
        UUID categoryId,
        UUID productId
    ) {
    }
}