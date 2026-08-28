package com.sup2i.food.promotion;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class PromotionLoyaltyE2EIntegrationTest {

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
                "PRM"
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
                "PRM-A"
            );

        otherStudent =
            insertStudentActor(
                organizationId,
                campusId,
                "PRM-B"
            );

        snackManager =
            insertRoleActor(
                organizationId,
                "PRM-MGR",
                "SNACK_MANAGER"
            );

        direction =
            insertRoleActor(
                organizationId,
                "PRM-DIR",
                "DIRECTION"
            );
    }

    // =========================================================
    // 01 - VALIDATE AND APPLY PERCENTAGE COUPON
    // =========================================================

    @Test
    void studentValidatesAndAppliesPercentageCoupon() throws Exception {

        CatalogFixture catalog =
            insertProduct(
                "PACKAGED",
                "SOLDE",
                "25.00"
            );

        UUID orderId =
            createDraftOrder(
                catalog.productId(),
                2
            );

        UUID couponId =
            createCoupon(
                couponBody(
                    "Solde 20%",
                    "PROMO-" + suffix(),
                    "PERCENTAGE",
                    "20",
                    null,
                    "ALL",
                    null,
                    null,
                    "5",
                    "2",
                    iso(nowMinusDays(1)),
                    iso(nowPlusDays(30)),
                    "E2E coupon"
                )
            );

        validateOrder(
            orderId,
            couponCode(couponId).toLowerCase()
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.eligible")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.eligibleAmount")
                    .value(50.0)
            )
            .andExpect(
                jsonPath("$.discountAmount")
                    .value(10.0)
            )
            .andExpect(
                jsonPath("$.coupon.id")
                    .value(couponId.toString())
            );

        applyCoupon(
            orderId,
            couponCode(couponId)
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.discountAmount")
                    .value(10.0)
            )
            .andExpect(
                jsonPath("$.discountTotal")
                    .value(10.0)
            )
            .andExpect(
                jsonPath("$.subtotal")
                    .value(50.0)
            )
            .andExpect(
                jsonPath("$.total")
                    .value(40.0)
            );

        assertThat(couponUsageCount(couponId))
            .isEqualTo(1L);
        assertThat(orderDiscountCount(orderId))
            .isEqualTo(1L);

        applyCoupon(
            orderId,
            couponCode(couponId)
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("COUPON_INELIGIBLE")
            );
    }

    // =========================================================
    // 02 - GLOBAL USAGE LIMIT REACHED
    // =========================================================

    @Test
    void globalUsageLimitRejectsFurtherOrders() throws Exception {

        CatalogFixture catalog =
            insertProduct(
                "PACKAGED",
                "LIMIT",
                "10.00"
            );

        UUID orderA =
            createDraftOrder(
                catalog.productId(),
                1
            );

        UUID orderB =
            createDraftOrder(
                catalog.productId(),
                1
            );

        UUID couponId =
            createCoupon(
                couponBody(
                    "Limite",
                    "LIM-" + suffix(),
                    "FIXED_AMOUNT",
                    "3.00",
                    null,
                    "ALL",
                    null,
                    null,
                    "1",
                    "1",
                    iso(nowMinusDays(1)),
                    iso(nowPlusDays(30)),
                    null
                )
            );

        applyCoupon(
            orderA,
            couponCode(couponId)
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.total")
                    .value(7.0)
            );

        applyCoupon(
            orderB,
            couponCode(couponId)
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("USAGE_LIMIT_REACHED")
            );

        validateOrder(
            orderB,
            couponCode(couponId)
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.eligible")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.reason")
                    .value("Coupon usage limit has been reached.")
            );
    }

    // =========================================================
    // 03 - PRODUCT SCOPE AND EXPIRY
    // =========================================================

    @Test
    void productScopeAndExpiryGovernEligibility() throws Exception {

        CatalogFixture target =
            insertProduct(
                "PACKAGED",
                "TARGET",
                "25.00"
            );

        CatalogFixture other =
            insertProduct(
                "PACKAGED",
                "OTHER",
                "15.00"
            );

        UUID productScoped =
            createCoupon(
                couponBody(
                    "Cible produit",
                    "SCOPE-" + suffix(),
                    "PERCENTAGE",
                    "10",
                    null,
                    "PRODUCT",
                    target.productId().toString(),
                    null,
                    "5",
                    "2",
                    iso(nowMinusDays(1)),
                    iso(nowPlusDays(30)),
                    null
                )
            );

        UUID withoutScopeOrder =
            createDraftOrder(
                other.productId(),
                1
            );

        validateOrder(
            withoutScopeOrder,
            couponCode(productScoped)
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.eligible")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.reason")
                    .value("Coupon does not apply to any item in this order.")
            );

        applyCoupon(
            withoutScopeOrder,
            couponCode(productScoped)
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("COUPON_INELIGIBLE")
            );

        UUID scopedOrder =
            createDraftOrder(
                target.productId(),
                1
            );

        applyCoupon(
            scopedOrder,
            couponCode(productScoped)
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.total")
                    .value(22.5)
            );

        UUID expired =
            createCoupon(
                couponBody(
                    "Épuisé",
                    "EXPIRED-" + suffix(),
                    "PERCENTAGE",
                    "10",
                    null,
                    "ALL",
                    null,
                    null,
                    "5",
                    "2",
                    iso(nowMinusDays(2)),
                    iso(nowMinusHours(1)),
                    null
                )
            );

        applyCoupon(
            scopedOrder,
            couponCode(expired)
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("COUPON_INELIGIBLE")
            );
    }

    // =========================================================
    // 04 - ADMIN CRUD AND RBAC
    // =========================================================

    @Test
    void adminCouponCrudAndRbac() throws Exception {

        mockMvc.perform(
                get("/api/v1/admin/coupons")
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.content")
                    .isArray()
            );

        UUID couponId =
            createCoupon(
                couponBody(
                    "RBAC coupon",
                    "RBAC-" + suffix(),
                    "PERCENTAGE",
                    "10",
                    null,
                    "ALL",
                    null,
                    null,
                    "5",
                    "2",
                    iso(nowMinusDays(1)),
                    iso(nowPlusDays(30)),
                    null
                )
            );

        mockMvc.perform(
                get(
                    "/api/v1/admin/coupons/{couponId}",
                    couponId
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
                jsonPath("$.id")
                    .value(couponId.toString())
            )
            .andExpect(
                jsonPath("$.code")
                    .value(couponCode(couponId))
            );

        mockMvc.perform(
                post("/api/v1/admin/coupons")
                    .header(
                        "Authorization",
                        bearer(direction)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        couponBody(
                            "Forbidden",
                            "RBAC-DIR-" + suffix(),
                            "PERCENTAGE",
                            "10",
                            null,
                            "ALL",
                            null,
                            null,
                            "5",
                            "2",
                            iso(nowMinusDays(1)),
                            iso(nowPlusDays(30)),
                            null
                        )
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                post("/api/v1/admin/coupons")
                    .header(
                        "Authorization",
                        bearer(student)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        couponBody(
                            "Forbidden student",
                            "RBAC-STU-" + suffix(),
                            "PERCENTAGE",
                            "10",
                            null,
                            "ALL",
                            null,
                            null,
                            "5",
                            "2",
                            iso(nowMinusDays(1)),
                            iso(nowPlusDays(30)),
                            null
                        )
                    )
            )
            .andExpect(
                status().isForbidden()
            );

        mockMvc.perform(
                post(
                    "/api/v1/admin/coupons/{couponId}/deactivate",
                    couponId
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
                jsonPath("$.active")
                    .value(false)
            );

        CatalogFixture catalog =
            insertProduct(
                "PACKAGED",
                "DEACT",
                "10.00"
            );

        UUID orderId =
            createDraftOrder(
                catalog.productId(),
                1
            );

        applyCoupon(
            orderId,
            couponCode(couponId)
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("COUPON_INELIGIBLE")
            );
    }

    // =========================================================
    // 05 - COUPON REQUIRES DRAFT AND OWNERSHIP
    // =========================================================

    @Test
    void couponRequiresDraftAndOwnership() throws Exception {

        CatalogFixture catalog =
            insertProduct(
                "PACKAGED",
                "OWND",
                "10.00"
            );

        UUID paidOrderId =
            createPaidOrder(
                catalog.productId(),
                1
            );

        UUID couponId =
            createCoupon(
                couponBody(
                    "Ownership",
                    "OWN-" + suffix(),
                    "PERCENTAGE",
                    "10",
                    null,
                    "ALL",
                    null,
                    null,
                    "5",
                    "2",
                    iso(nowMinusDays(1)),
                    iso(nowPlusDays(30)),
                    null
                )
            );

        applyCoupon(
            paidOrderId,
            couponCode(couponId)
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("CONFLICT")
            );

        UUID draftOrderId =
            createDraftOrder(
                catalog.productId(),
                1
            );

        mockMvc.perform(
                post("/api/v1/coupons/apply")
                    .header(
                        "Authorization",
                        bearer(otherStudent)
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "orderId": "%s",
                          "code": "%s"
                        }
                        """.formatted(
                            draftOrderId,
                            couponCode(couponId)
                        )
                    )
            )
            .andExpect(
                status().isNotFound()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("NOT_FOUND")
            );
    }

    // =========================================================
    // 06 - LOYALTY BALANCE, ADJUST, REDEEM
    // =========================================================

    @Test
    void loyaltyAdjustRedeemAndInsufficientBalance() throws Exception {

        getBalance(student)
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.balance")
                    .value(0)
            );

        adjust(
            snackManager,
            student.userId(),
            100,
            "Ajustement E2E"
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.points")
                    .value(100)
            )
            .andExpect(
                jsonPath("$.newBalance")
                    .value(100)
            );

        adjust(
            direction,
            student.userId(),
            10,
            "Interdit"
        )
            .andExpect(
                status().isForbidden()
            );

        getBalance(student)
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.balance")
                    .value(100)
            )
            .andExpect(
                jsonPath("$.lifetimeEarned")
                    .value(100)
            );

        CatalogFixture catalog =
            insertProduct(
                "PACKAGED",
                "REDEEM",
                "25.00"
            );

        UUID orderId =
            createDraftOrder(
                catalog.productId(),
                1
            );

        redeem(
            orderId,
            100,
            student
        )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.pointsRedeemed")
                    .value(100)
            )
            .andExpect(
                jsonPath("$.rewardMad")
                    .value(5.0)
            )
            .andExpect(
                jsonPath("$.newBalance")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.discountTotal")
                    .value(5.0)
            )
            .andExpect(
                jsonPath("$.total")
                    .value(20.0)
            );

        redeem(
            orderId,
            100,
            student
        )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("INSUFFICIENT_LOYALTY_BALANCE")
            );

        redeem(
            orderId,
            90,
            student
        )
            .andExpect(
                status().isBadRequest()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_ERROR")
            );

        assertThat(orderDiscountCount(orderId))
            .isEqualTo(1L);
        assertThat(loyaltyTransactionCount(student, "REDEEM"))
            .isEqualTo(1L);
    }

    // =========================================================
    // 07 - POINTS EARNED ON PAID ORDER (IDEMPOTENT)
    // =========================================================

    @Test
    void pointsEarnedOnceOnPaidOrder() throws Exception {

        CatalogFixture catalog =
            insertProduct(
                "PACKAGED",
                "EARN",
                "10.00"
            );

        UUID orderId =
            createPaidOrder(
                catalog.productId(),
                2
            );

        getBalance(student)
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.balance")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.lifetimeEarned")
                    .value(2)
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
            )
            .andExpect(
                jsonPath("$.replayed")
                    .value(true)
            );

        getBalance(student)
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.balance")
                    .value(2)
            );

        assertThat(loyaltyTransactionCount(student, "EARN"))
            .isEqualTo(1L);
    }

    // =========================================================
    // HTTP HELPERS
    // =========================================================

    private ResultActions applyCoupon(
        UUID orderId,
        String code
    ) throws Exception {

        return mockMvc.perform(
            post("/api/v1/coupons/apply")
                .header(
                    "Authorization",
                    bearer(student)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "orderId": "%s",
                      "code": "%s"
                    }
                    """.formatted(orderId, code)
                )
        );
    }

    private ResultActions validateOrder(
        UUID orderId,
        String code
    ) throws Exception {

        return mockMvc.perform(
            post("/api/v1/coupons/validate")
                .header(
                    "Authorization",
                    bearer(student)
                )
                .contentType(
                    MediaType.APPLICATION_JSON
                )
                .content(
                    """
                    {
                      "orderId": "%s",
                      "code": "%s"
                    }
                    """.formatted(orderId, code)
                )
        );
    }

    private ResultActions redeem(
        UUID orderId,
        int points,
        Actor actor
    ) throws Exception {

        return mockMvc.perform(
            post("/api/v1/loyalty/redeem")
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
                      "orderId": "%s",
                      "points": %d
                    }
                    """.formatted(orderId, points)
                )
        );
    }

    private ResultActions getBalance(
        Actor actor
    ) throws Exception {

        return mockMvc.perform(
            get("/api/v1/loyalty/balance")
                .header(
                    "Authorization",
                    bearer(actor)
                )
        );
    }

    private ResultActions adjust(
        Actor actor,
        UUID studentUserId,
        int points,
        String reason
    ) throws Exception {

        return mockMvc.perform(
            post("/api/v1/admin/loyalty/adjust")
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
                      "studentUserId": "%s",
                      "points": %d,
                      "reason": "%s"
                    }
                    """.formatted(
                            studentUserId,
                            points,
                            reason
                        )
                )
        );
    }

    private String couponBody(
        String name,
        String code,
        String type,
        String discountValue,
        String maxDiscountAmount,
        String targetType,
        String targetId,
        String minQuantity,
        String usageLimitTotal,
        String usageLimitPerStudent,
        String startsAt,
        String endsAt,
        String description
    ) {

        return """
            {
              "name": "%s",
              "code": "%s",
              "type": "%s",
              "discountValue": %s,
              "maxDiscountAmount": %s,
              "targetType": "%s",
              "targetIds": %s,
              "minQuantity": %s,
              "usageLimitTotal": %s,
              "usageLimitPerStudent": %s,
              "startsAt": "%s",
              "endsAt": "%s",
              "description": %s
            }
            """.formatted(
                name,
                code,
                type,
                discountValue,
                textOrNull(maxDiscountAmount),
                targetType,
                listOrNull(targetId),
                textOrNull(minQuantity),
                textOrNull(usageLimitTotal),
                textOrNull(usageLimitPerStudent),
                startsAt,
                endsAt,
                textOrNull(description)
            );
    }

    private UUID createCoupon(
        String body
    ) throws Exception {

        String response =
            mockMvc.perform(
                    post("/api/v1/admin/coupons")
                        .header(
                            "Authorization",
                            bearer(snackManager)
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

        return UUID.fromString(
            extractField(response, "id")
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
                "PAID",
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
            )
            .andExpect(
                jsonPath("$.order.status")
                    .value("AWAITING_PAYMENT")
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

    private String draftBody(
        UUID productId,
        int quantity
    ) {

        return """
            {
              "locationId": "%s",
              "currency": "MAD",
              "customerNote": "Promotions E2E",
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
            "prm-"
                + prefix.toLowerCase()
                + "-"
                + suffix
                + "@sup2i.test",
            "Promo",
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
            "prm-"
                + prefix.toLowerCase()
                + "-"
                + suffix()
                + "@sup2i.test",
            "Promo",
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
                "prm-e2e-"
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
        String productType,
        String prefix,
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
            prefix + " Category",
            "category-" + suffix()
        );

        UUID productId =
            UUID.randomUUID();

        boolean prepared =
            "PREPARED".equals(
                productType
            );

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
                TRUE, ?, TRUE
            )
            """,
            productId,
            organizationId,
            categoryId,
            prefix + "-" + suffix(),
            prefix + " Product",
            productType,
            new BigDecimal(price),
            prepared
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
    // DATABASE ASSERTION HELPERS
    // =========================================================

    private long couponUsageCount(
        UUID couponId
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM coupon_usages
                WHERE coupon_id = ?
                """,
                Long.class,
                couponId
            );

        return count == null
            ? 0L
            : count;
    }

    private long orderDiscountCount(
        UUID orderId
    ) {

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM order_discounts
                WHERE order_id = ?
                """,
                Long.class,
                orderId
            );

        return count == null
            ? 0L
            : count;
    }

    private long loyaltyTransactionCount(
        Actor actor,
        String type
    ) {

        UUID studentId =
            actor.studentId();

        Long count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM loyalty_transactions lt
                JOIN loyalty_accounts la
                  ON la.id = lt.account_id
                WHERE la.student_id = ?
                  AND lt.type = ?
                """,
                Long.class,
                studentId,
                type
            );

        return count == null
            ? 0L
            : count;
    }

    // =========================================================
    // SMALL HELPERS
    // =========================================================

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

    private String couponCode(
        UUID couponId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT code
            FROM coupons
            WHERE id = ?
            """,
            String.class,
            couponId
        );
    }

    private String extractField(
        String json,
        String path
    ) {

        try {

            com.fasterxml.jackson.databind.JsonNode node =
                JSON.readTree(json);

            for (
                String segment
                : path.split("\\.")
            ) {

                node =
                    node.get(segment);

                if (
                    node == null
                ) {
                    throw new IllegalStateException(
                        "Field "
                            + path
                            + " not found in: "
                            + json
                    );
                }
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

    private String textOrNull(
        String value
    ) {

        return value == null
            ? "null"
            : "\"" + value + "\"";
    }

    private String listOrNull(
        String value
    ) {

        return value == null
            ? "null"
            : "[\"" + value + "\"]";
    }

    private String iso(
        OffsetDateTime dateTime
    ) {

        return dateTime.format(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME
        );
    }

    private OffsetDateTime nowMinusDays(
        int days
    ) {

        return OffsetDateTime.now()
            .minusDays(days);
    }

    private OffsetDateTime nowMinusHours(
        int hours
    ) {

        return OffsetDateTime.now()
            .minusHours(hours);
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

    private static final com.fasterxml.jackson.databind.ObjectMapper
        JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();
}