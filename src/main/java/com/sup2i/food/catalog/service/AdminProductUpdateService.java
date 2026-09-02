package com.sup2i.food.catalog.service;

import com.sup2i.food.catalog.api.dto.AdminUpdateProductRequest;
import com.sup2i.food.catalog.api.dto.ProductResponse;
import com.sup2i.food.catalog.domain.ProductType;
import com.sup2i.food.catalog.exception.CatalogConflictException;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AdminProductUpdateService {

    private final JdbcTemplate jdbcTemplate;

    public AdminProductUpdateService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public ProductResponse update(
        UUID actorUserId,
        UUID productId,
        AdminUpdateProductRequest request,
        boolean mayUpdatePrice
    ) {

        Actor actor =
            actor(
                actorUserId
            );

        ProductRow current =
            productForUpdate(
                actor.organizationId(),
                productId
            );

        UUID categoryId =
            request.categoryId() == null
                ? current.categoryId()
                : request.categoryId();

        requireCategory(
            actor.organizationId(),
            categoryId
        );

        String sku =
            request.sku() == null
                ? current.sku()
                : requiredTrimmed(
                    request.sku(),
                    "sku"
                );

        String name =
            request.name() == null
                ? current.name()
                : requiredTrimmed(
                    request.name(),
                    "name"
                );

        String description =
            request.description() == null
                ? current.description()
                : trimToNull(
                    request.description()
                );

        String imageUrl =
            request.imageUrl() == null
                ? current.imageUrl()
                : trimToNull(
                    request.imageUrl()
                );

        ProductType productType =
            request.productType() == null
                ? current.productType()
                : request.productType();

        BigDecimal basePrice =
            request.basePrice() == null
                ? current.basePrice()
                : request.basePrice();

        BigDecimal taxRate =
            request.taxRate() == null
                ? current.taxRate()
                : request.taxRate();

        Integer preparationMinutes =
            request.preparationMinutes() == null
                ? current.preparationMinutes()
                : request.preparationMinutes();

        boolean trackStock =
            request.trackStock() == null
                ? current.trackStock()
                : request.trackStock();

        boolean prepared =
            request.prepared() == null
                ? current.prepared()
                : request.prepared();

        boolean active =
            request.active() == null
                ? current.active()
                : request.active();

        boolean basePriceChanged =
            basePrice.compareTo(
                current.basePrice()
            ) != 0;

        boolean taxRateChanged =
            taxRate.compareTo(
                current.taxRate()
            ) != 0;

        if (basePriceChanged && !mayUpdatePrice) {

            throw new AccessDeniedException(
                "price.update is required to change basePrice."
            );
        }

        if (
            !sku.equalsIgnoreCase(
                current.sku()
            )
        ) {

            Integer duplicate =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM products
                    WHERE organization_id = ?
                      AND LOWER(sku) = LOWER(?)
                      AND id <> ?
                    """,
                    Integer.class,
                    actor.organizationId(),
                    sku,
                    productId
                );

            if (
                duplicate != null
                    && duplicate > 0
            ) {

                throw new CatalogConflictException(
                    "Product SKU already exists."
                );
            }
        }

        OffsetDateTime now =
            OffsetDateTime.now();

        int updated =
            jdbcTemplate.update(
                """
                UPDATE products
                SET
                    category_id = ?,
                    sku = ?,
                    name = ?,
                    description = ?,
                    image_url = ?,
                    product_type = ?,
                    base_price = ?,
                    tax_rate = ?,
                    preparation_minutes = ?,
                    track_stock = ?,
                    is_prepared = ?,
                    is_active = ?,
                    updated_at = ?
                WHERE id = ?
                  AND organization_id = ?
                """,
                categoryId,
                sku,
                name,
                description,
                imageUrl,
                productType.name(),
                basePrice,
                taxRate,
                preparationMinutes,
                trackStock,
                prepared,
                active,
                now,
                productId,
                actor.organizationId()
            );

        if (updated != 1) {

            throw new CatalogNotFoundException(
                "Product does not exist."
            );
        }

        if (
            basePriceChanged
                || taxRateChanged
        ) {

            jdbcTemplate.update(
                """
                UPDATE product_price_history
                SET effective_to = ?
                WHERE product_id = ?
                  AND effective_to IS NULL
                """,
                now,
                productId
            );

            jdbcTemplate.update(
                """
                INSERT INTO product_price_history (
                    id,
                    product_id,
                    price,
                    tax_rate,
                    effective_from,
                    effective_to,
                    changed_by,
                    reason
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    NULL,
                    ?,
                    ?
                )
                """,
                UUID.randomUUID(),
                productId,
                basePrice,
                taxRate,
                now,
                actor.userId(),
                basePriceChanged
                    ? "ADMIN_PRODUCT_PRICE_UPDATE"
                    : "ADMIN_PRODUCT_TAX_UPDATE"
            );
        }

        return response(
            actor.organizationId(),
            productId
        );
    }

    private Actor actor(
        UUID actorUserId
    ) {

        if (actorUserId == null) {

            throw new BadCredentialsException(
                "Authenticated user does not exist."
            );
        }

        List<Actor> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    organization_id,
                    status
                FROM users
                WHERE id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new Actor(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "organization_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "status"
                        )
                    ),
                actorUserId
            );

        if (rows.size() != 1) {

            throw new BadCredentialsException(
                "Authenticated user does not exist."
            );
        }

        Actor actor =
            rows.get(0);

        if (
            !"ACTIVE".equals(
                actor.status()
            )
        ) {

            throw new BadCredentialsException(
                "Authenticated user is inactive."
            );
        }

        return actor;
    }

    private ProductRow productForUpdate(
        UUID organizationId,
        UUID productId
    ) {

        List<ProductRow> rows =
            jdbcTemplate.query(
                """
                SELECT
                    p.id,
                    p.category_id,
                    p.sku,
                    p.name,
                    p.description,
                    p.image_url,
                    p.product_type,
                    p.base_price,
                    p.tax_rate,
                    p.preparation_minutes,
                    p.track_stock,
                    p.is_prepared,
                    p.is_active
                FROM products p
                WHERE p.id = ?
                  AND p.organization_id = ?
                FOR UPDATE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new ProductRow(
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
                            "name"
                        ),
                        resultSet.getString(
                            "description"
                        ),
                        resultSet.getString(
                            "image_url"
                        ),
                        ProductType.valueOf(
                            resultSet.getString(
                                "product_type"
                            )
                        ),
                        resultSet.getBigDecimal(
                            "base_price"
                        ),
                        resultSet.getBigDecimal(
                            "tax_rate"
                        ),
                        resultSet.getObject(
                            "preparation_minutes",
                            Integer.class
                        ),
                        resultSet.getBoolean(
                            "track_stock"
                        ),
                        resultSet.getBoolean(
                            "is_prepared"
                        ),
                        resultSet.getBoolean(
                            "is_active"
                        )
                    ),
                productId,
                organizationId
            );

        if (rows.isEmpty()) {

            throw new CatalogNotFoundException(
                "Product does not exist."
            );
        }

        if (rows.size() != 1) {

            throw new IllegalStateException(
                "Product lookup returned multiple rows."
            );
        }

        return rows.get(0);
    }

    private void requireCategory(
        UUID organizationId,
        UUID categoryId
    ) {

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM categories
                WHERE id = ?
                  AND organization_id = ?
                """,
                Integer.class,
                categoryId,
                organizationId
            );

        if (
            count == null
                || count != 1
        ) {

            throw new CatalogNotFoundException(
                "Category does not exist."
            );
        }
    }

    private ProductResponse response(
        UUID organizationId,
        UUID productId
    ) {

        List<ProductResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    p.id,
                    p.category_id,
                    c.name AS category_name,
                    p.sku,
                    p.name,
                    p.description,
                    p.image_url,
                    p.product_type,
                    p.base_price,
                    p.tax_rate,
                    p.preparation_minutes,
                    p.track_stock,
                    p.is_prepared,
                    p.is_active
                FROM products p
                JOIN categories c
                  ON c.id = p.category_id
                 AND c.organization_id = p.organization_id
                WHERE p.id = ?
                  AND p.organization_id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new ProductResponse(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "category_id",
                            UUID.class
                        ),
                        resultSet.getString(
                            "category_name"
                        ),
                        resultSet.getString(
                            "sku"
                        ),
                        resultSet.getString(
                            "name"
                        ),
                        resultSet.getString(
                            "description"
                        ),
                        resultSet.getString(
                            "image_url"
                        ),
                        ProductType.valueOf(
                            resultSet.getString(
                                "product_type"
                            )
                        ),
                        resultSet.getBigDecimal(
                            "base_price"
                        ),
                        resultSet.getBigDecimal(
                            "tax_rate"
                        ),
                        resultSet.getObject(
                            "preparation_minutes",
                            Integer.class
                        ),
                        resultSet.getBoolean(
                            "track_stock"
                        ),
                        resultSet.getBoolean(
                            "is_prepared"
                        ),
                        resultSet.getBoolean(
                            "is_active"
                        )
                    ),
                productId,
                organizationId
            );

        if (rows.size() != 1) {

            throw new CatalogNotFoundException(
                "Product does not exist."
            );
        }

        return rows.get(0);
    }

    private String requiredTrimmed(
        String value,
        String field
    ) {

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {

            throw new CatalogConflictException(
                field
                    + " must not be blank."
            );
        }

        return normalized;
    }

    private String trimToNull(
        String value
    ) {

        String normalized =
            value.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }

    private record Actor(
        UUID userId,
        UUID organizationId,
        String status
    ) {
    }

    private record ProductRow(
        UUID id,
        UUID categoryId,
        String sku,
        String name,
        String description,
        String imageUrl,
        ProductType productType,
        BigDecimal basePrice,
        BigDecimal taxRate,
        Integer preparationMinutes,
        boolean trackStock,
        boolean prepared,
        boolean active
    ) {
    }
}