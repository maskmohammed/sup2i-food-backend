package com.sup2i.food.review.repository;

import com.sup2i.food.review.domain.ModerationStatus;
import com.sup2i.food.review.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository
    extends JpaRepository<Review, UUID> {

    boolean existsByStudent_IdAndProduct_Id(
        UUID studentId,
        UUID productId
    );

    boolean existsByStudent_IdAndOrder_Id(
        UUID studentId,
        UUID orderId
    );

    @Query("""
        select r
        from Review r
        left join r.product p
        where r.moderationStatus = :moderationStatus
          and (
                (p is not null and p.id = :productId)
                or (
                    r.order is not null
                    and exists (
                        select 1
                        from OrderItem oi
                        where oi.order.id = r.order.id
                          and oi.product.id = :productId
                    )
                )
              )
        order by r.createdAt desc
        """)
    Page<Review> findVisibleForProduct(
        @Param("productId")
        UUID productId,

        @Param("moderationStatus")
        ModerationStatus moderationStatus,

        Pageable pageable
    );

    Page<Review>
        findAllByModerationStatusOrderByCreatedAtAsc(
            ModerationStatus moderationStatus,
            Pageable pageable
        );

    @Query("""
        select r
        from Review r
        left join r.order o
        left join r.product p
        where r.id = :reviewId
          and (
                (o is not null and o.organization.id = :organizationId)
                or (p is not null and p.organization.id = :organizationId)
              )
        """)
    Optional<Review> findOwnedByOrganization(
        @Param("reviewId")
        UUID reviewId,

        @Param("organizationId")
        UUID organizationId
    );
}