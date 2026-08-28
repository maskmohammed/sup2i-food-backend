package com.sup2i.food.menuvote.service;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.StudentRepository;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.menuvote.api.dto.CreateMenuVoteSessionRequest;
import com.sup2i.food.menuvote.api.dto.MenuVoteOptionRequest;
import com.sup2i.food.menuvote.api.dto.MenuVoteResultResponse;
import com.sup2i.food.menuvote.api.dto.MenuVoteSessionResponse;
import com.sup2i.food.menuvote.api.dto.VoteRequest;
import com.sup2i.food.menuvote.domain.MenuVote;
import com.sup2i.food.menuvote.domain.MenuVoteOption;
import com.sup2i.food.menuvote.domain.MenuVoteSession;
import com.sup2i.food.menuvote.domain.MenuVoteStatus;
import com.sup2i.food.menuvote.exception.MenuVoteConflictException;
import com.sup2i.food.menuvote.exception.MenuVoteNotFoundException;
import com.sup2i.food.menuvote.exception.MenuVoteValidationException;
import com.sup2i.food.menuvote.repository.MenuVoteRepository;
import com.sup2i.food.menuvote.repository.MenuVoteSessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MenuVoteService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ProductRepository productRepository;
    private final MenuVoteSessionRepository sessionRepository;
    private final MenuVoteRepository voteRepository;

    public MenuVoteService(
        UserRepository userRepository,
        StudentRepository studentRepository,
        ProductRepository productRepository,
        MenuVoteSessionRepository sessionRepository,
        MenuVoteRepository voteRepository
    ) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.productRepository = productRepository;
        this.sessionRepository = sessionRepository;
        this.voteRepository = voteRepository;
    }

    // =========================================================
    // ADMIN OPERATIONS
    // =========================================================

    @Transactional
    public MenuVoteSessionResponse create(
        UUID actorId,
        CreateMenuVoteSessionRequest request
    ) {
        User actor =
            requiredUser(actorId);

        validateDeadline(request);

        MenuVoteSession session =
            new MenuVoteSession(
                actor.getOrganization(),
                request.title().trim(),
                request.targetWeek(),
                request.voteDeadline(),
                actor
            );

        session.setDescription(
            trimToNull(request.description())
        );

        var requests =
            request.options();

        for (
            int index = 0;
            index < requests.size();
            index++
        ) {
            MenuVoteOption option =
                toOption(
                    actor,
                    requests.get(index),
                    index
                );

            session.addOption(option);
        }

        return MenuVoteSessionResponse.from(
            sessionRepository.save(session)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<MenuVoteSessionResponse> list(
        UUID actorId,
        MenuVoteStatus status,
        int page,
        int size
    ) {
        User actor =
            requiredUser(actorId);

        Page<MenuVoteSession> sessions =
            status == null
                ? sessionRepository
                    .findAllByOrganization_Id(
                        actor.getOrganization().getId(),
                        PageRequest.of(
                            page,
                            size,
                            Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                            )
                        )
                    )
                : sessionRepository
                    .findAllByOrganization_IdAndStatus(
                        actor.getOrganization().getId(),
                        status,
                        PageRequest.of(
                            page,
                            size,
                            Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                            )
                        )
                    );

        return PageResponse.from(
            sessions.map(MenuVoteSessionResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public MenuVoteSessionResponse get(
        UUID actorId,
        UUID sessionId
    ) {
        User actor =
            requiredUser(actorId);

        return MenuVoteSessionResponse.from(
            requiredSession(
                sessionId,
                actor
            )
        );
    }

    @Transactional
    public MenuVoteSessionResponse close(
        UUID actorId,
        UUID sessionId
    ) {
        User actor =
            requiredUser(actorId);

        MenuVoteSession session =
            requiredSession(
                sessionId,
                actor
            );

        if (session.getStatus() != MenuVoteStatus.OPEN) {
            throw new MenuVoteConflictException(
                "La session de vote est déjà clôturée."
            );
        }

        session.setStatus(MenuVoteStatus.CLOSED);

        return MenuVoteSessionResponse.from(
            sessionRepository.save(session)
        );
    }

    @Transactional(readOnly = true)
    public MenuVoteResultResponse results(
        UUID actorId,
        UUID sessionId
    ) {
        User actor =
            requiredUser(actorId);

        MenuVoteSession session =
            requiredSession(
                sessionId,
                actor
            );

        List<MenuVoteResultResponse.OptionResult>
            options =
                new ArrayList<>();

        long totalVotes =
            0;

        for (
            MenuVoteOption option
            : session.getOptions()
        ) {
            long votes =
                voteRepository
                    .countBySession_IdAndOption_Id(
                        session.getId(),
                        option.getId()
                    );

            totalVotes += votes;

            options.add(
                new MenuVoteResultResponse.OptionResult(
                    option.getId(),
                    option.getProduct() == null
                        ? null
                        : option.getProduct().getId(),
                    option.getLabel(),
                    votes
                )
            );
        }

        return new MenuVoteResultResponse(
            session.getId(),
            session.getTitle(),
            session.getStatus(),
            (int) totalVotes,
            List.copyOf(options)
        );
    }

    // =========================================================
    // STUDENT OPERATIONS
    // =========================================================

    @Transactional(readOnly = true)
    public MenuVoteSessionResponse current(
        UUID actorId
    ) {
        Student student =
            requiredStudent(actorId);

        UUID organizationId =
            student
                .getCampus()
                .getOrganization()
                .getId();

        MenuVoteSession session =
            sessionRepository
                .findFirstByOrganization_IdAndStatusAndVoteDeadlineGreaterThanOrderByVoteDeadlineAsc(
                    organizationId,
                    MenuVoteStatus.OPEN,
                    OffsetDateTime.now()
                )
                .orElseThrow(() ->
                    new MenuVoteNotFoundException(
                        "Aucune session de vote ouverte."
                    )
                );

        return MenuVoteSessionResponse.from(session);
    }

    @Transactional
    public MenuVoteSessionResponse vote(
        UUID actorId,
        UUID sessionId,
        VoteRequest request
    ) {
        Student student =
            requiredStudent(actorId);

        MenuVoteSession session =
            sessionRepository
                .findByIdAndOrganization_Id(
                    sessionId,
                    student
                        .getCampus()
                        .getOrganization()
                        .getId()
                )
                .orElseThrow(() ->
                    new MenuVoteNotFoundException(
                        "Session de vote introuvable."
                    )
                );

        OffsetDateTime now =
            OffsetDateTime.now();

        if (
            !MenuVotePolicy.isOpen(
                session.getStatus(),
                now,
                session.getVoteDeadline()
            )
        ) {
            throw new MenuVoteConflictException(
                "La session de vote n'est plus ouverte."
            );
        }

        if (
            voteRepository
                .existsBySession_IdAndStudent_Id(
                    session.getId(),
                    student.getId()
                )
        ) {
            throw new MenuVoteConflictException(
                "Vous avez déjà voté pour cette session."
            );
        }

        MenuVoteOption option =
            session.getOptions()
                .stream()
                .filter(candidate ->
                    candidate.getId()
                        .equals(request.optionId())
                )
                .findFirst()
                .orElseThrow(() ->
                    new MenuVoteConflictException(
                        "Cette option n'appartient pas à la session."
                    )
                );

        try {
            voteRepository.save(
                new MenuVote(
                    session,
                    option,
                    student
                )
            );
        } catch (DataIntegrityViolationException exception) {
            throw new MenuVoteConflictException(
                "Vous avez déjà voté pour cette session."
            );
        }

        return MenuVoteSessionResponse.from(session);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private void validateDeadline(
        CreateMenuVoteSessionRequest request
    ) {
        if (
            request.voteDeadline()
                .isBefore(OffsetDateTime.now())
        ) {
            throw new MenuVoteValidationException(
                "La date limite de vote doit être future."
            );
        }

        if (
            ChronoUnit.DAYS.between(
                request.targetWeek(),
                request.voteDeadline().toLocalDate()
            ) < 0
        ) {
            throw new MenuVoteValidationException(
                "La date limite de vote doit être après la semaine cible."
            );
        }
    }

    private MenuVoteOption toOption(
        User actor,
        MenuVoteOptionRequest request,
        int displayOrder
    ) {
        Product product =
            null;

        if (request.productId() != null) {
            product =
                productRepository
                    .findCatalogProduct(
                        request.productId(),
                        actor.getOrganization().getId()
                    )
                    .orElseThrow(() ->
                        new MenuVoteValidationException(
                            "Le produit lié à l'option est introuvable."
                        )
                    );
        }

        return new MenuVoteOption(
            product,
            request.label().trim(),
            trimToNull(request.description()),
            displayOrder
        );
    }

    private MenuVoteSession requiredSession(
        UUID sessionId,
        User actor
    ) {
        return sessionRepository
            .findByIdAndOrganization_Id(
                sessionId,
                actor.getOrganization().getId()
            )
            .orElseThrow(() ->
                new MenuVoteNotFoundException(
                    "Session de vote introuvable."
                )
            );
    }

    private User requiredUser(
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

    private Student requiredStudent(
        UUID actorId
    ) {
        return studentRepository
            .findByUserId(actorId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated student does not exist."
                )
            );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}