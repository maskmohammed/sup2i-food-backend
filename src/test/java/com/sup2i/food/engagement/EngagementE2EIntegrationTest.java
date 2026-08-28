package com.sup2i.food.engagement;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.security.service.AuthenticationTokens;
import com.sup2i.food.security.service.RefreshTokenService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class EngagementE2EIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private UUID organizationId;
    private UUID campusId;
    private UUID locationId;
    private UUID timeSlotId;

    private Actor student;
    private Actor otherStudent;
    private Actor snackManager;
    private Actor direction;

    @BeforeEach
    void seedTenant() {

        organizationId =
            insertOrganization(
                "ENG"
            );

        campusId =
            insertCampus(
                organizationId,
                "MAIN",
                true
            );

        locationId =
            insertLocation(
                campusId,
                "SNACK",
                "SNACK",
                true
            );

        timeSlotId =
            insertTimeSlot(
                locationId,
                25
            );

        student =
            insertStudentActor(
                organizationId,
                campusId,
                "ENG-A"
            );

        otherStudent =
            insertStudentActor(
                organizationId,
                campusId,
                "ENG-B"
            );

        snackManager =
            insertRoleActor(
                organizationId,
                "ENG-MGR",
                "SNACK_MANAGER"
            );

        direction =
            insertRoleActor(
                organizationId,
                "ENG-DIR",
                "DIRECTION"
            );
    }

    // =========================================================
    // 01 - REVIEWS : SUBMIT, MODERATE, PUBLIC LISTING
    // =========================================================

    @Test
    void studentPostsReviewAndManagerModerates() throws Exception {

        CatalogFixture catalog =
            insertProduct(
                "PACKAGED",
                "REV",
                "25.00"
            );

        UUID orderId =
            createPaidOrder(
                catalog.productId(),
                1
            );

        markDelivered(orderId);

        postReview(
            student,
            reviewBody(
                null,
                orderId,
                5,
                "Excellent repas.",
                new String[]{"photo-a", "photo-b"}
            )
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.orderId")
                    .value(orderId.toString())
            )
            .andExpect(
                jsonPath("$.rating")
                    .value(5)
            )
            .andExpect(
                jsonPath("$.moderationStatus")
                    .value("PENDING")
            );

        postReview(
            student,
            reviewBody(
                null,
                orderId,
                3,
                null,
                null
            )
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );

        mockMvc.perform(
                get(
                    "/api/v1/products/{productId}/reviews",
                    catalog.productId()
                )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.content")
                    .isEmpty()
            );

        UUID reviewId =
            firstPendingReviewId(
                snackManager
            );

        mockMvc.perform(
                patch(
                    "/api/v1/admin/reviews/{reviewId}/moderate",
                    reviewId
                )
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        { "status": "APPROVED" }
                        """
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                patch(
                    "/api/v1/admin/reviews/{reviewId}/moderate",
                    reviewId
                )
                    .header(
                        "Authorization",
                        bearer(snackManager)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        { "status": "APPROVED" }
                        """
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.moderationStatus")
                    .value("APPROVED")
            );

        mockMvc.perform(
                get(
                    "/api/v1/products/{productId}/reviews",
                    catalog.productId()
                )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.content.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.content[0].comment")
                    .value("Excellent repas.")
            );

        postReview(
            otherStudent,
            reviewBody(
                catalog.productId(),
                null,
                4,
                "Très bon.",
                null
            )
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.productId")
                    .value(catalog.productId().toString())
            );

        postReview(
            otherStudent,
            reviewBody(
                catalog.productId(),
                null,
                1,
                null,
                null
            )
        )
            .andExpect(
                status().isConflict()
            );
    }

    // =========================================================
    // 02 - SURVEYS : CREATE, PUBLISH, TARGET, RESPOND, RESULTS
    // =========================================================

    @Test
    void surveyLifecycleTargetingAndResults() throws Exception {

        mockMvc.perform(
                post("/api/v1/admin/surveys")
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        surveyBody(
                            "Bloqué",
                            "ALL"
                        )
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        CatalogFixture catalog =
            insertProduct(
                "PACKAGED",
                "SUR",
                "20.00"
            );

        UUID deliveredOrderId =
            createPaidOrder(
                catalog.productId(),
                1
            );

        markDelivered(deliveredOrderId);

        String openSurvey =
            createSurvey(
                snackManager,
                surveyBody(
                    "Enquête satisfaction",
                    "ALL"
                )
            );

        UUID openSurveyId =
            idOf(openSurvey);

        UUID ratingQuestionId =
            arrayFieldId(
                openSurvey,
                "questions",
                0
            );

        UUID textQuestionId =
            arrayFieldId(
                openSurvey,
                "questions",
                1
            );

        publishSurvey(
            openSurveyId,
            snackManager
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("ACTIVE")
            );

        String orderedSurvey =
            createSurvey(
                snackManager,
                surveyBody(
                    "Enquête ciblée commande",
                    "ORDERED"
                )
            );

        UUID orderedSurveyId =
            idOf(orderedSurvey);

        UUID orderedQuestionId =
            arrayFieldId(
                orderedSurvey,
                "questions",
                0
            );

        publishSurvey(
            orderedSurveyId,
            snackManager
        )
            .andExpect(
                status().isOk()
            );

        activeSurveys(student)
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.length()")
                    .value(2)
            );

        respondSurvey(
            openSurveyId,
            ratingQuestionId,
            textQuestionId,
            "Très satisfait.",
            student
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.surveyId")
                    .value(openSurveyId.toString())
            );

        respondSurvey(
            openSurveyId,
            ratingQuestionId,
            textQuestionId,
            "Doublon",
            student
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );

        respondSurvey(
            orderedSurveyId,
            orderedQuestionId,
            null,
            null,
            student
        )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/surveys/{surveyId}/results",
                    openSurveyId
                )
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.title")
                    .value("Enquête satisfaction")
            )
            .andExpect(
                jsonPath("$.questions.length()")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.questions[0].totalResponses")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.questions[0].counts['5']")
                    .value(1)
            );

        closeSurvey(
            openSurveyId,
            snackManager
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("CLOSED")
            );
    }

    // =========================================================
    // 03 - MENU VOTE : SESSION, VOTING, CLOSE, RBAC
    // =========================================================

    @Test
    void menuVoteSessionVotingAndRbac() throws Exception {

        CatalogFixture catalog =
            insertProduct(
                "PACKAGED",
                "VOTE",
                "15.00"
            );

        mockMvc.perform(
                post("/api/v1/admin/menu-votes")
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        voteSessionBody(
                            "Bloqué",
                            catalog.productId()
                        )
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        String sessionJson =
            createVoteSession(
                snackManager,
                voteSessionBody(
                    "Menus semaine prochaine",
                    catalog.productId()
                )
            );

        UUID sessionId =
            idOf(sessionJson);

        UUID optionId =
            arrayFieldId(
                sessionJson,
                "options",
                0
            );

        currentSession(student)
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.id")
                    .value(sessionId.toString())
            );

        mockMvc.perform(
                post(
                    "/api/v1/menu-votes/{sessionId}/vote",
                    sessionId
                )
                    .header(
                        "Authorization",
                        bearer(otherStudent)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        voteBody(optionId)
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                get(
                    "/api/v1/menu-votes/{sessionId}/results",
                    sessionId
                )
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.totalVotes")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.options[0].votes")
                    .value(1)
            );

        mockMvc.perform(
                patch(
                    "/api/v1/admin/menu-votes/{sessionId}/close",
                    sessionId
                )
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                patch(
                    "/api/v1/admin/menu-votes/{sessionId}/close",
                    sessionId
                )
                    .header(
                        "Authorization",
                        bearer(snackManager)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.status")
                    .value("CLOSED")
            );

        mockMvc.perform(
                post(
                    "/api/v1/menu-votes/{sessionId}/vote",
                    sessionId
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        voteBody(optionId)
                    )
            )
            .andExpect(
                status().isConflict()
            );

        mockMvc.perform(
                post("/api/v1/admin/menu-votes")
                    .header(
                        "Authorization",
                        bearer(student)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        voteSessionBody(
                            "Refus étudiant",
                            catalog.productId()
                        )
                    )
            )
            .andExpect(
                status().isForbidden()
            );
    }

    // =========================================================
    // HTTP HELPERS
    // =========================================================

    private ResultActions postReview(
        Actor actor,
        String body
    ) throws Exception {

        return mockMvc.perform(
            post("/api/v1/reviews")
                .header(
                    "Authorization",
                    bearer(actor)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(body)
        );
    }

    private UUID firstPendingReviewId(
        Actor actor
    ) throws Exception {

        String response =
            mockMvc.perform(
                    get("/api/v1/admin/reviews/pending")
                        .header(
                            "Authorization",
                            bearer(actor)
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        return UUID.fromString(
            field(
                response,
                "content[0].id"
            )
        );
    }

    private String createSurvey(
        Actor actor,
        String body
    ) throws Exception {

        return mockMvc.perform(
                post("/api/v1/admin/surveys")
                    .header(
                        "Authorization",
                        bearer(actor)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(body)
            )
            .andExpect(
                status().isOk()
            )
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private ResultActions publishSurvey(
        UUID surveyId,
        Actor actor
    ) throws Exception {

        return mockMvc.perform(
            patch(
                "/api/v1/admin/surveys/{surveyId}/publish",
                surveyId
            )
                .header(
                    "Authorization",
                    bearer(actor)
                )
        );
    }

    private ResultActions closeSurvey(
        UUID surveyId,
        Actor actor
    ) throws Exception {

        return mockMvc.perform(
            patch(
                "/api/v1/admin/surveys/{surveyId}/close",
                surveyId
            )
                .header(
                    "Authorization",
                    bearer(actor)
                )
        );
    }

    private ResultActions activeSurveys(
        Actor actor
    ) throws Exception {

        return mockMvc.perform(
            get("/api/v1/surveys/active")
                .header(
                    "Authorization",
                    bearer(actor)
                )
        );
    }

    private ResultActions respondSurvey(
        UUID surveyId,
        UUID ratingQuestionId,
        UUID textQuestionId,
        String comment,
        Actor actor
    ) throws Exception {

        if (textQuestionId == null) {
            return mockMvc.perform(
                post(
                    "/api/v1/surveys/{surveyId}/respond",
                    surveyId
                )
                    .header(
                        "Authorization",
                        bearer(actor)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "answers": {
                            "%s": 5
                          }
                        }
                        """.formatted(ratingQuestionId)
                    )
            );
        }

        return mockMvc.perform(
            post(
                "/api/v1/surveys/{surveyId}/respond",
                surveyId
            )
                .header(
                    "Authorization",
                    bearer(actor)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "answers": {
                        "%s": 5,
                        "%s": "%s"
                      }
                    }
                    """.formatted(
                            ratingQuestionId,
                            textQuestionId,
                            comment
                        )
                )
        );
    }

    private String createVoteSession(
        Actor actor,
        String body
    ) throws Exception {

        return mockMvc.perform(
                post("/api/v1/admin/menu-votes")
                    .header(
                        "Authorization",
                        bearer(actor)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(body)
            )
            .andExpect(
                status().isOk()
            )
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private ResultActions currentSession(
        Actor actor
    ) throws Exception {

        return mockMvc.perform(
            get("/api/v1/menu-votes/current")
                .header(
                    "Authorization",
                    bearer(actor)
                )
        );
    }

    // =========================================================
    // ORDER HELPERS
    // =========================================================

    private UUID createDraftOrder(
        UUID productId,
        int quantity
    ) throws Exception {

        UUID orderId =
            UUID.randomUUID();

        mockMvc.perform(
                put(
                    "/api/v1/orders/{orderId}",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        draftBody(productId, quantity)
                    )
            )
            .andExpect(
                status().isOk()
            );

        return orderId;
    }

    private UUID createPaidOrder(
        UUID productId,
        int quantity
    ) throws Exception {

        UUID orderId =
            createDraftOrder(
                productId,
                quantity
            );

        UUID stockItemId =
            insertProductStockItem(
                organizationId,
                productId,
                "PIECE"
            );

        UUID stockLocationId =
            insertStockLocation(
                locationId,
                "ENG-PAID",
                true
            );

        insertBalance(
            stockItemId,
            stockLocationId,
            "20.000",
            "0.000"
        );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/submit",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/begin-payment",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isOk()
            );

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/pay",
                    orderId
                )
                    .header(
                        "Authorization",
                        bearer(student)
                    )
            )
            .andExpect(
                status().isOk()
            );

        return orderId;
    }

    private void markDelivered(
        UUID orderId
    ) {

        jdbcTemplate.update(
            """
            UPDATE orders
            SET status = 'COMPLETED'
            WHERE id = ?
            """,
            orderId
        );
    }

    private String draftBody(
        UUID productId,
        int quantity
    ) {

        return """
            {
              "locationId": "%s",
              "currency": "MAD",
              "customerNote": "Engagement E2E",
              "timeSlotId": "%s",
              "items": [
                {
                  "productId": "%s",
                  "quantity": %d
                }
              ]
            }
            """.formatted(
                locationId,
                timeSlotId,
                productId,
                quantity
            );
    }

    // =========================================================
    // REQUEST BODIES
    // =========================================================

    private String reviewBody(
        UUID productId,
        UUID orderId,
        int rating,
        String comment,
        String[] photos
    ) {

        return """
            {
              "productId": %s,
              "orderId": %s,
              "rating": %d,
              "comment": %s,
              "photos": %s
            }
            """.formatted(
                textOrNull(
                    productId == null
                        ? null
                        : productId.toString()
                ),
                textOrNull(
                    orderId == null
                        ? null
                        : orderId.toString()
                ),
                rating,
                textOrNull(comment),
                photoList(photos)
            );
    }

    private String surveyBody(
        String title,
        String target
    ) {

        return """
            {
              "title": "%s",
              "description": "Description E2E",
              "target": "%s",
              "startsAt": null,
              "endsAt": null,
              "questions": [
                {
                  "question": "Notez votre satisfaction (1-5)",
                  "type": "RATING",
                  "options": null,
                  "required": true
                },
                {
                  "question": "Commentaire libre",
                  "type": "TEXT",
                  "options": null,
                  "required": false
                }
              ]
            }
            """.formatted(title, target);
    }

    private String voteSessionBody(
        String title,
        UUID productId
    ) {

        return """
            {
              "title": "%s",
              "description": "Votez pour la semaine prochaine",
              "targetWeek": "%s",
              "voteDeadline": "%s",
              "options": [
                {
                  "productId": "%s",
                  "label": "Menu du snack A",
                  "description": "Option A"
                },
                {
                  "productId": null,
                  "label": "Menu du snack B",
                  "description": "Option B"
                }
              ]
            }
            """.formatted(
                title,
                nowPlusDays(7)
                    .toLocalDate(),
                iso(nowPlusDays(9)),
                productId
            );
    }

    private String voteBody(
        UUID optionId
    ) {

        return """
            {
              "optionId": "%s"
            }
            """.formatted(optionId);
    }

    // =========================================================
    // TENANT / IDENTITY FIXTURES
    // =========================================================

    private UUID insertOrganization(
        String prefix
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO organizations (
                id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, TRUE)
            """,
            id,
            prefix + " Organization",
            prefix + suffix()
        );

        return id;
    }

    private UUID insertCampus(
        UUID tenantId,
        String prefix,
        boolean active
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO campuses (
                id,
                organization_id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            id,
            tenantId,
            prefix + " Campus",
            "C" + suffix(),
            active
        );

        return id;
    }

    private UUID insertLocation(
        UUID selectedCampusId,
        String prefix,
        String type,
        boolean active
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO locations (
                id,
                campus_id,
                name,
                code,
                type,
                is_active
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            id,
            selectedCampusId,
            prefix + " Location",
            "L" + suffix(),
            type,
            active
        );

        return id;
    }

    private UUID insertTimeSlot(
        UUID selectedLocationId,
        int capacity
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO time_slots (
                id,
                location_id,
                slot_date,
                start_time,
                end_time,
                capacity,
                reserved_count
            )
            VALUES (
                ?, ?,
                CURRENT_DATE + 1,
                '12:00', '12:15',
                ?, 0
            )
            """,
            id,
            selectedLocationId,
            capacity
        );

        return id;
    }

    private Actor insertStudentActor(
        UUID tenantId,
        UUID selectedCampusId,
        String prefix
    ) {

        UUID userId =
            UUID.randomUUID();

        String suffix =
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO users (
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
            tenantId,
            "eng-"
                + prefix.toLowerCase()
                + "-"
                + suffix
                + "@sup2i.test",
            "Engage",
            prefix
        );

        UUID studentId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO students (
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
            selectedCampusId,
            "STU-" + suffix
        );

        return new Actor(
            userId,
            studentId,
            token(userId)
        );
    }

    private Actor insertRoleActor(
        UUID tenantId,
        String prefix,
        String roleCode
    ) {

        UUID userId =
            insertUser(
                tenantId,
                prefix
            );

        UUID roleId =
            jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM roles
                WHERE code = ?
                """,
                UUID.class,
                roleCode
            );

        jdbcTemplate.update(
            """
            INSERT INTO user_roles (
                id,
                user_id,
                role_id
            )
            VALUES (?, ?, ?)
            """,
            UUID.randomUUID(),
            userId,
            roleId
        );

        return new Actor(
            userId,
            null,
            token(userId)
        );
    }

    private UUID insertUser(
        UUID tenantId,
        String prefix
    ) {

        UUID userId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO users (
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
            tenantId,
            "eng-"
                + prefix.toLowerCase()
                + "-"
                + suffix()
                + "@sup2i.test",
            "Engage",
            prefix
        );

        return userId;
    }

    private String token(
        UUID userId
    ) {

        User user =
            userRepository
                .findById(userId)
                .orElseThrow();

        AuthenticationTokens tokens =
            refreshTokenService.issue(
                user,
                "eng-e2e-"
                    + suffix(),
                InetAddress
                    .getLoopbackAddress()
            );

        return tokens.accessToken();
    }

    private String bearer(
        Actor requestActor
    ) {

        return "Bearer "
            + requestActor.token();
    }

    // =========================================================
    // CATALOG FIXTURES
    // =========================================================

    private CatalogFixture insertProduct(
        String prefix,
        String categoryName,
        String price
    ) {

        UUID categoryId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO categories (
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
            categoryName + " Category",
            "category-" + suffix()
        );

        UUID productId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO products (
                id,
                organization_id,
                category_id,
                sku,
                name,
                product_type,
                base_price,
                tax_rate,
                preparation_minutes,
                track_stock,
                is_prepared,
                is_active
            )
            VALUES (
                ?, ?, ?, ?, ?, ?,
                ?, 0.00, 0,
                TRUE, FALSE, TRUE
            )
            """,
            productId,
            organizationId,
            categoryId,
            prefix + "-" + suffix(),
            prefix + " Product",
            "PACKAGED",
            new BigDecimal(price)
        );

        return new CatalogFixture(
            productId,
            categoryId
        );
    }

    private UUID insertProductStockItem(
        UUID tenantId,
        UUID productId,
        String unit
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_items (
                id,
                organization_id,
                product_id,
                base_unit,
                track_expiry
            )
            VALUES (?, ?, ?, ?, FALSE)
            """,
            id,
            tenantId,
            productId,
            unit
        );

        return id;
    }

    private UUID insertStockLocation(
        UUID selectedLocationId,
        String prefix,
        boolean active
    ) {

        UUID id =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO stock_locations (
                id,
                location_id,
                name,
                type,
                is_active
            )
            VALUES (?, ?, ?, 'STORAGE', ?)
            """,
            id,
            selectedLocationId,
            prefix + " Stock",
            active
        );

        return id;
    }

    private void insertBalance(
        UUID stockItemId,
        UUID stockLocationId,
        String physical,
        String reserved
    ) {

        jdbcTemplate.update(
            """
            INSERT INTO stock_balances (
                stock_item_id,
                stock_location_id,
                physical_quantity,
                reserved_quantity
            )
            VALUES (?, ?, ?, ?)
            """,
            stockItemId,
            stockLocationId,
            new BigDecimal(physical),
            new BigDecimal(reserved)
        );
    }

    // =========================================================
    // JSON HELPERS
    // =========================================================

    private UUID idOf(
        String json
    ) {

        return UUID.fromString(
            field(json, "id")
        );
    }

    private UUID arrayFieldId(
        String json,
        String arrayPath,
        int index
    ) {

        return UUID.fromString(
            arrayField(
                json,
                arrayPath,
                index,
                "id"
            )
        );
    }

    private String field(
        String json,
        String path
    ) {

        try {

            com.fasterxml.jackson.databind.JsonNode node =
                JSON.readTree(json);

            for (
                String segment
                : tokenize(path)
            ) {

                if (node == null) {
                    throw new IllegalStateException(
                        "Field "
                            + path
                            + " not found in: "
                            + json
                    );
                }

                if (segment.endsWith("]")) {
                    String name =
                        segment.substring(
                            0,
                            segment.indexOf('[')
                        );
                    int index =
                        Integer.parseInt(
                            segment.substring(
                                segment.indexOf('[') + 1,
                                segment.length() - 1
                            )
                        );
                    node =
                        node.get(name)
                            .get(index);
                }
                else {
                    node =
                        node.get(segment);
                }
            }

            if (node == null) {
                throw new IllegalStateException(
                    "Field "
                        + path
                        + " not found in: "
                        + json
                );
            }

            return node.asText();

        } catch (
            java.io.IOException exception
        ) {
            throw new IllegalStateException(
                "Could not parse JSON: "
                    + json,
                exception
            );
        }
    }

    private String arrayField(
        String json,
        String arrayPath,
        int index,
        String fieldName
    ) {

        try {

            com.fasterxml.jackson.databind.JsonNode node =
                JSON.readTree(json);

            for (
                String segment
                : arrayPath.split("\\.")
            ) {

                node =
                    node.get(segment);
            }

            return node.get(index)
                .get(fieldName)
                .asText();

        } catch (
            java.io.IOException exception
        ) {
            throw new IllegalStateException(
                "Could not parse JSON: "
                    + json,
                exception
            );
        }
    }

    private static java.util.List<String> tokenize(
        String path
    ) {

        return java.util.Arrays.stream(
                path.split("\\.")
            )
            .toList();
    }

    private String textOrNull(
        String value
    ) {

        return value == null
            ? "null"
            : "\"" + value + "\"";
    }

    private String photoList(
        String[] photos
    ) {

        if (
            photos == null
        ) {
            return "null";
        }

        return java.util.Arrays.stream(photos)
            .map(photo ->
                "\"" + photo + "\""
            )
            .collect(
                java.util.stream.Collectors.joining(
                    ",",
                    "[",
                    "]"
                )
            );
    }

    private String iso(
        OffsetDateTime dateTime
    ) {

        return dateTime.format(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME
        );
    }

    private OffsetDateTime nowPlusDays(
        int days
    ) {

        return OffsetDateTime.now()
            .plusDays(days);
    }

    private String suffix() {

        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);
    }

    private record CatalogFixture(
        UUID productId,
        UUID categoryId
    ) {
    }

    private record Actor(
        UUID userId,
        UUID studentId,
        String token
    ) {
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper
        JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();
}