package com.sup2i.food.security.api;

import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.StudentRepository;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.security.api.dto.MeResponse;
import com.sup2i.food.security.api.dto.StudentSummaryResponse;
import com.sup2i.food.security.service.AuthorizationSnapshotService;
import com.sup2i.food.security.service.AuthorizationSnapshotService.AuthorizationSnapshot;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Tag(name = "Security", description = "Authentication, MFA, tokens, and the current-user profile.")
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final AuthorizationSnapshotService authorizationService;

    public MeController(
        UserRepository userRepository,
        StudentRepository studentRepository,
        AuthorizationSnapshotService authorizationService
    ) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public MeResponse me(
        JwtAuthenticationToken authentication
    ) {
        UUID userId;

        try {
            userId = UUID.fromString(
                authentication.getToken().getSubject()
            );
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException(
                "Invalid JWT subject."
            );
        }

        User user = userRepository
            .findById(userId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user does not exist."
                )
            );

        AuthorizationSnapshot authorization =
            authorizationService.load(userId);

        StudentSummaryResponse studentResponse =
            studentRepository
                .findByUserId(userId)
                .map(this::toStudentSummary)
                .orElse(null);

        return new MeResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getStatus().name(),
            authorization.allRoles(),
            studentResponse
        );
    }

    private StudentSummaryResponse toStudentSummary(
        Student student
    ) {
        return new StudentSummaryResponse(
            student.getId(),
            student.getStudentNumber(),
            student.getProgram(),
            student.getLevel(),
            student.getGroupName(),
            null
        );
    }
}