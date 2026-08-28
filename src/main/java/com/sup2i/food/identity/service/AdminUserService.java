package com.sup2i.food.identity.service;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.identity.api.dto.AdminUserMutationResponse;
import com.sup2i.food.identity.api.dto.AdminUserResponse;
import com.sup2i.food.identity.domain.Role;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.domain.UserRole;
import com.sup2i.food.identity.domain.UserStatus;
import com.sup2i.food.identity.exception.RoleNotFoundException;
import com.sup2i.food.identity.exception.UserNotFoundException;
import com.sup2i.food.identity.repository.RoleRepository;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.identity.repository.UserRoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private static final int
        MAX_PAGE_SIZE =
            100;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public AdminUserService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        UserRoleRepository userRoleRepository
    ) {
        this.userRepository =
            userRepository;

        this.roleRepository =
            roleRepository;

        this.userRoleRepository =
            userRoleRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(
        UUID actorId,
        int page,
        int size
    ) {

        User actor =
            resolveActor(actorId);

        int safePage =
            Math.max(page, 0);

        int safeSize =
            Math.min(
                Math.max(size, 1),
                MAX_PAGE_SIZE
            );

        PageRequest pageable =
            PageRequest.of(
                safePage,
                safeSize,
                Sort.by(
                    Sort.Order.asc("email"),
                    Sort.Order.asc("id")
                )
            );

        Page<AdminUserResponse> result =
            userRepository
                .findAllByOrganization_Id(
                    actor.getOrganization()
                        .getId(),
                    pageable
                )
                .map(this::toResponse);

        return PageResponse.from(
            result
        );
    }

    @Transactional(readOnly = true)
    public AdminUserResponse find(
        UUID actorId,
        UUID targetUserId
    ) {

        User actor =
            resolveActor(actorId);

        User target =
            ownedUser(
                targetUserId,
                actor
            );

        return toResponse(target);
    }

    @Transactional
    public AdminUserMutationResponse activate(
        UUID actorId,
        UUID targetUserId
    ) {

        User actor =
            resolveActor(actorId);

        User target =
            ownedUser(
                targetUserId,
                actor
            );

        if (
            target.getStatus()
                == UserStatus.ACTIVE
        ) {
            return new AdminUserMutationResponse(
                toResponse(target),
                true
            );
        }

        target.setStatus(
            UserStatus.ACTIVE
        );

        userRepository.saveAndFlush(
            target
        );

        return new AdminUserMutationResponse(
            toResponse(target),
            false
        );
    }

    @Transactional
    public AdminUserMutationResponse deactivate(
        UUID actorId,
        UUID targetUserId
    ) {

        User actor =
            resolveActor(actorId);

        User target =
            ownedUser(
                targetUserId,
                actor
            );

        if (
            target.getStatus()
                == UserStatus.SUSPENDED
        ) {
            return new AdminUserMutationResponse(
                toResponse(target),
                true
            );
        }

        target.setStatus(
            UserStatus.SUSPENDED
        );

        userRepository.saveAndFlush(
            target
        );

        return new AdminUserMutationResponse(
            toResponse(target),
            false
        );
    }

    @Transactional
    public AdminUserMutationResponse assignRole(
        UUID actorId,
        UUID targetUserId,
        String roleCode
    ) {

        User actor =
            resolveActor(actorId);

        User target =
            ownedUser(
                targetUserId,
                actor
            );

        Role role =
            roleRepository
                .findByCode(roleCode)
                .orElseThrow(() ->
                    new RoleNotFoundException(
                        "Role does not exist."
                    )
                );

        boolean alreadyAssigned =
            userRoleRepository
                .findByUser_IdAndRole_CodeAndCampusIsNullAndLocationIsNull(
                    target.getId(),
                    role.getCode()
                )
                .isPresent();

        if (alreadyAssigned) {
            return new AdminUserMutationResponse(
                toResponse(target),
                true
            );
        }

        UserRole assignment =
            new UserRole(target, role);

        assignment.setAssignedBy(
            actor
        );

        userRoleRepository
            .saveAndFlush(assignment);

        return new AdminUserMutationResponse(
            toResponse(target),
            false
        );
    }

    @Transactional
    public AdminUserMutationResponse revokeRole(
        UUID actorId,
        UUID targetUserId,
        String roleCode
    ) {

        User actor =
            resolveActor(actorId);

        User target =
            ownedUser(
                targetUserId,
                actor
            );

        UserRole assignment =
            userRoleRepository
                .findByUser_IdAndRole_CodeAndCampusIsNullAndLocationIsNull(
                    target.getId(),
                    roleCode
                )
                .orElse(null);

        if (assignment == null) {
            return new AdminUserMutationResponse(
                toResponse(target),
                true
            );
        }

        userRoleRepository.delete(
            assignment
        );

        userRoleRepository.flush();

        return new AdminUserMutationResponse(
            toResponse(target),
            false
        );
    }

    private User ownedUser(
        UUID targetUserId,
        User actor
    ) {

        return userRepository
            .findByIdAndOrganization_Id(
                targetUserId,
                actor.getOrganization()
                    .getId()
            )
            .orElseThrow(() ->
                new UserNotFoundException(
                    "User does not exist."
                )
            );
    }

    private User resolveActor(
        UUID actorId
    ) {

        return userRepository
            .findById(actorId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user does not exist."
                )
            );
    }

    private AdminUserResponse toResponse(
        User user
    ) {

        List<UserRole> assignments =
            userRoleRepository
                .findAllWithRoleAndPermissionsByUserId(
                    user.getId()
                );

        Set<String> roles =
            assignments.stream()
                .map(assignment ->
                    assignment.getRole()
                        .getCode()
                )
                .collect(
                    Collectors.toCollection(
                        java.util.LinkedHashSet::new
                    )
                );

        return new AdminUserResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getStatus().name(),
            roles
        );
    }
}
