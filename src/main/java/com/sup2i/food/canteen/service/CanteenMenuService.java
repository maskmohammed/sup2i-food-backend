package com.sup2i.food.canteen.service;

import com.sup2i.food.canteen.api.dto.CanteenMenuResponse;
import com.sup2i.food.canteen.api.dto.CanteenProductSummaryResponse;
import com.sup2i.food.canteen.exception.CanteenErrorCode;
import com.sup2i.food.canteen.exception.CanteenException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CanteenMenuService {

    private final JdbcTemplate jdbcTemplate;

    public CanteenMenuService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<CanteenMenuResponse> list(
        UUID actorId,
        UUID locationId,
        LocalDate from,
        LocalDate to
    ) {

        if (locationId == null) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "locationId is required."
            );
        }

        boolean invalidRange =
            from != null
                && to != null
                && from.isAfter(to);

        if (invalidRange) {

            throw new CanteenException(
                CanteenErrorCode.VALIDATION_ERROR,
                "from must be before or equal to to."
            );
        }

        UUID organizationId =
            organizationId(
                actorId
            );

        StringBuilder sql =
            new StringBuilder(
                """
                SELECT
                    cm.id,
                    cm.location_id,
                    cm.menu_date,
                    cm.meal_type,
                    cm.title,
                    cm.description,
                    cm.status
                FROM canteen_menus cm
                JOIN locations l
                  ON l.id = cm.location_id
                JOIN campuses c
                  ON c.id = l.campus_id
                WHERE cm.location_id = ?
                  AND c.organization_id = ?
                  AND l.is_active = TRUE
                  AND c.is_active = TRUE
                """
            );

        List<Object> arguments =
            new ArrayList<>();

        arguments.add(
            locationId
        );

        arguments.add(
            organizationId
        );

        if (from != null) {

            sql.append(
                """
                  AND cm.menu_date >= ?
                """
            );

            arguments.add(
                from
            );
        }

        if (to != null) {

            sql.append(
                """
                  AND cm.menu_date <= ?
                """
            );

            arguments.add(
                to
            );
        }

        sql.append(
            """
                ORDER BY
                    cm.menu_date ASC,
                    CASE cm.meal_type
                        WHEN 'BREAKFAST' THEN 1
                        WHEN 'LUNCH' THEN 2
                        WHEN 'DINNER' THEN 3
                        ELSE 4
                    END ASC,
                    cm.id ASC
            """
        );

        Object[] parameters =
            arguments.toArray(
                new Object[0]
            );

        List<MenuRow> rows =
            jdbcTemplate.query(
                sql.toString(),
                this::mapMenu,
                parameters
            );

        List<CanteenMenuResponse> responses =
            new ArrayList<>();

        for (MenuRow row : rows) {

            responses.add(
                new CanteenMenuResponse(
                    row.id(),
                    row.date(),
                    row.mealType(),
                    row.title(),
                    row.description(),
                    row.status(),
                    products(
                        row.id(),
                        row.locationId(),
                        row.date()
                    )
                )
            );
        }

        return List.copyOf(
            responses
        );
    }

    private List<CanteenProductSummaryResponse>
        products(
            UUID menuId,
            UUID locationId,
            LocalDate menuDate
        ) {

        return jdbcTemplate.query(
            """
            SELECT
                p.id,
                p.category_id,
                p.sku,
                primary_barcode.barcode,
                p.name,
                p.image_url,
                p.base_price,
                p.is_active,
                (
                    p.is_active = TRUE
                    AND category.is_active = TRUE
                    AND COALESCE(
                        pls.is_enabled,
                        TRUE
                    ) = TRUE
                    AND (
                        pls.allowed_days IS NULL
                        OR EXTRACT(
                            ISODOW FROM CAST(? AS DATE)
                        )::SMALLINT =
                            ANY(pls.allowed_days)
                    )
                ) AS available
            FROM canteen_menu_choices cmc
            JOIN products p
              ON p.id = cmc.product_id
            JOIN categories category
              ON category.id = p.category_id
            LEFT JOIN product_location_settings pls
              ON pls.product_id = p.id
             AND pls.location_id = ?
            LEFT JOIN LATERAL (
                SELECT pb.barcode
                FROM product_barcodes pb
                WHERE pb.product_id = p.id
                  AND pb.is_active = TRUE
                ORDER BY
                    pb.is_primary DESC,
                    pb.created_at ASC,
                    pb.id ASC
                LIMIT 1
            ) primary_barcode
              ON TRUE
            WHERE cmc.canteen_menu_id = ?
              AND cmc.is_active = TRUE
            ORDER BY
                cmc.display_order ASC,
                p.name ASC,
                p.id ASC
            """,
            (
                resultSet,
                rowNumber
            ) ->
                new CanteenProductSummaryResponse(
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                    resultSet.getObject(
                        "category_id",
                        UUID.class
                    ),
                    resultSet.getString(
                        "sku"
                    ),
                    resultSet.getString(
                        "barcode"
                    ),
                    resultSet.getString(
                        "name"
                    ),
                    resultSet.getString(
                        "image_url"
                    ),
                    resultSet.getObject(
                        "base_price",
                        BigDecimal.class
                    ),
                    "MAD",
                    resultSet.getBoolean(
                        "available"
                    ),
                    resultSet.getBoolean(
                        "is_active"
                    )
                ),
            menuDate,
            locationId,
            menuId
        );
    }

    private MenuRow mapMenu(
        java.sql.ResultSet resultSet,
        int rowNumber
    ) throws java.sql.SQLException {

        return new MenuRow(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "location_id",
                UUID.class
            ),
            resultSet.getObject(
                "menu_date",
                LocalDate.class
            ),
            resultSet.getString(
                "meal_type"
            ),
            resultSet.getString(
                "title"
            ),
            resultSet.getString(
                "description"
            ),
            resultSet.getString(
                "status"
            )
        );
    }

    private UUID organizationId(
        UUID actorId
    ) {

        if (actorId == null) {

            throw new BadCredentialsException(
                "Authenticated user identifier is missing."
            );
        }

        List<UUID> organizations =
            jdbcTemplate.query(
                """
                SELECT u.organization_id
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    resultSet.getObject(
                        "organization_id",
                        UUID.class
                    ),
                actorId
            );

        if (organizations.isEmpty()) {

            throw new BadCredentialsException(
                "Authenticated user does not exist or is inactive."
            );
        }

        if (organizations.size() != 1) {

            throw new IllegalStateException(
                "Authenticated organization lookup returned multiple rows."
            );
        }

        return organizations.get(0);
    }

    private record MenuRow(
        UUID id,
        UUID locationId,
        LocalDate date,
        String mealType,
        String title,
        String description,
        String status
    ) {
    }
}
