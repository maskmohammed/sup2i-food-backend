package com.sup2i.food.promotion.repository;

import com.sup2i.food.promotion.domain.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CouponUsageRepository
    extends JpaRepository<CouponUsage, UUID> {

    boolean existsByCoupon_IdAndOrder_Id(
        UUID couponId,
        UUID orderId
    );

    long countByCoupon_Id(
        UUID couponId
    );

    long countByCoupon_IdAndStudent_Id(
        UUID couponId,
        UUID studentId
    );
}