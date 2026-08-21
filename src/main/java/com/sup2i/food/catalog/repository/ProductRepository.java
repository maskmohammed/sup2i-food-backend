package com.sup2i.food.catalog.repository;

import com.sup2i.food.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository
    extends JpaRepository<Product, UUID> {

    boolean existsByOrganization_IdAndSkuIgnoreCase(
        UUID organizationId,
        String sku
    );

    @Query(
        value = """
            select p
            from Product p
            join fetch p.category c
            where p.organization.id = :organizationId
              and p.active = true
              and c.active = true
              and (
                    :categoryId is null
                    or c.id = :categoryId
                  )
            """,
        countQuery = """
            select count(p)
            from Product p
            join p.category c
            where p.organization.id = :organizationId
              and p.active = true
              and c.active = true
              and (
                    :categoryId is null
                    or c.id = :categoryId
                  )
            """
    )
    Page<Product> findCatalogProducts(
        @Param("organizationId")
        UUID organizationId,

        @Param("categoryId")
        UUID categoryId,

        Pageable pageable
    );

    @Query("""
        select p
        from Product p
        join fetch p.category c
        where p.id = :productId
          and p.organization.id = :organizationId
        """)
    Optional<Product>
        findCatalogProduct(
            @Param("productId")
            UUID productId,

            @Param("organizationId")
            UUID organizationId
        );
}