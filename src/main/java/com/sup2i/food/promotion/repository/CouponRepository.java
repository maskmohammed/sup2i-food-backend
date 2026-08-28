package com.sup2i.food.promotion.repository;

import com.sup2i.food.promotion.domain.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository
    extends JpaRepository<Coupon, UUID> {

    Optional<Coupon>
        findByOrganization_IdAndCode(
            UUID organizationId,
            String code
        );

    Page<Coupon>
        findByOrganization_Id(
            UUID organizationId,
            Pageable pageable
        );
}