package com.sup2i.food.identity.repository;

import com.sup2i.food.identity.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    @Query("""
        select distinct ur
        from UserRole ur
        join fetch ur.role r
        left join fetch r.permissions
        where ur.user.id = :userId
        """)
    List<UserRole> findAllWithRoleAndPermissionsByUserId(
        @Param("userId") UUID userId
    );
}
