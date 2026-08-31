package com.sup2i.food.voting.service;

import com.sup2i.food.voting.api.dto.CreateMenuProposalCommand;
import com.sup2i.food.voting.api.dto.MenuProposalResponse;
import com.sup2i.food.voting.domain.MenuProposalStatus;
import com.sup2i.food.voting.exception.MenuVotingConflictException;
import com.sup2i.food.voting.exception.MenuVotingNotFoundException;
import com.sup2i.food.voting.exception.MenuVotingValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class MenuProposalService {

    private final JdbcTemplate jdbcTemplate;

    public MenuProposalService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public MenuProposalResponse create(
        UUID actorId,
        UUID proposalId,
        CreateMenuProposalCommand command
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        requireId(
            proposalId,
            "Menu proposal id"
        );

        validateCreate(
            command
        );

        if (command.studentId() != null) {
            ownedStudent(
                organizationId,
                command.studentId()
            );
        }

        String title =
            requiredText(
                command.title(),
                "Proposal title",
                180
            );

        String description =
            nullableText(
                command.description()
            );

        MenuProposalResponse existing =
            find(
                organizationId,
                proposalId,
                false
            );

        if (existing != null) {

            boolean same =
                Objects.equals(
                    existing.studentId(),
                    command.studentId()
                )
                    && Objects.equals(
                        existing.title(),
                        title
                    )
                    && Objects.equals(
                        existing.description(),
                        description
                    );

            if (same) {
                return replay(
                    existing
                );
            }

            throw new MenuVotingConflictException(
                "Menu proposal identifier is already used by another payload."
            );
        }

        try {

            jdbcTemplate.update(
                """
                INSERT INTO menu_proposals(
                    id,
                    organization_id,
                    student_id,
                    title,
                    description,
                    status
                )
                VALUES (
                    ?, ?, ?, ?, ?,
                    'SUBMITTED'
                )
                """,
                proposalId,
                organizationId,
                command.studentId(),
                title,
                description
            );

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new MenuVotingConflictException(
                "Menu proposal violates a schema or tenant invariant."
            );
        }

        return get(
            actorId,
            proposalId
        );
    }

    @Transactional
    public MenuProposalResponse beginReview(
        UUID actorId,
        UUID proposalId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        MenuProposalResponse proposal =
            locked(
                organizationId,
                proposalId
            );

        if (
            proposal.status()
                == MenuProposalStatus.UNDER_REVIEW
        ) {
            return proposal;
        }

        if (
            proposal.status()
                != MenuProposalStatus.SUBMITTED
        ) {
            throw new MenuVotingConflictException(
                "Only a submitted proposal can enter review."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE menu_proposals
                SET
                    status = 'UNDER_REVIEW',
                    reviewed_by = ?,
                    reviewed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND organization_id = ?
                  AND status = 'SUBMITTED'
                """,
                actorId,
                proposalId,
                organizationId
            );

        if (updated != 1) {
            throw new MenuVotingConflictException(
                "Menu proposal changed concurrently."
            );
        }

        return get(
            actorId,
            proposalId
        );
    }

    @Transactional
    public MenuProposalResponse resolve(
        UUID actorId,
        UUID proposalId,
        MenuProposalStatus target
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        if (
            target != MenuProposalStatus.ACCEPTED
            && target != MenuProposalStatus.REJECTED
        ) {
            throw new MenuVotingValidationException(
                "Proposal resolution target must be ACCEPTED or REJECTED."
            );
        }

        MenuProposalResponse proposal =
            locked(
                organizationId,
                proposalId
            );

        if (
            proposal.status()
                == target
        ) {
            return proposal;
        }

        boolean resolvable =
            proposal.status()
                == MenuProposalStatus.SUBMITTED
                || proposal.status()
                    == MenuProposalStatus.UNDER_REVIEW;

        if (!resolvable) {
            throw new MenuVotingConflictException(
                "Only a submitted or under-review proposal can be resolved."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE menu_proposals
                SET
                    status = ?,
                    reviewed_by = ?,
                    reviewed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND organization_id = ?
                """,
                target.name(),
                actorId,
                proposalId,
                organizationId
            );

        if (updated != 1) {
            throw new MenuVotingConflictException(
                "Menu proposal changed concurrently."
            );
        }

        return get(
            actorId,
            proposalId
        );
    }

    @Transactional
    public MenuProposalResponse archive(
        UUID actorId,
        UUID proposalId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        MenuProposalResponse proposal =
            locked(
                organizationId,
                proposalId
            );

        if (
            proposal.status()
                == MenuProposalStatus.ARCHIVED
        ) {
            return proposal;
        }

        boolean terminal =
            proposal.status()
                == MenuProposalStatus.ACCEPTED
                || proposal.status()
                    == MenuProposalStatus.REJECTED;

        if (!terminal) {
            throw new MenuVotingConflictException(
                "Only an accepted or rejected proposal can be archived."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE menu_proposals
                SET status = 'ARCHIVED'
                WHERE id = ?
                  AND organization_id = ?
                """,
                proposalId,
                organizationId
            );

        if (updated != 1) {
            throw new MenuVotingConflictException(
                "Menu proposal changed concurrently."
            );
        }

        return get(
            actorId,
            proposalId
        );
    }

    @Transactional(readOnly = true)
    public MenuProposalResponse get(
        UUID actorId,
        UUID proposalId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        requireId(
            proposalId,
            "Menu proposal id"
        );

        MenuProposalResponse proposal =
            find(
                organizationId,
                proposalId,
                false
            );

        if (proposal == null) {
            throw new MenuVotingNotFoundException(
                "Menu proposal does not exist."
            );
        }

        return proposal;
    }

    @Transactional(readOnly = true)
    public List<MenuProposalResponse> list(
        UUID actorId,
        MenuProposalStatus status
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        if (status == null) {

            return jdbcTemplate.query(
                """
                SELECT
                    id,
                    organization_id,
                    student_id,
                    title,
                    description,
                    status,
                    reviewed_by,
                    reviewed_at,
                    created_at
                FROM menu_proposals
                WHERE organization_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                (resultSet, rowNumber) ->
                    response(
                        resultSet,
                        false
                    ),
                organizationId
            );
        }

        return jdbcTemplate.query(
            """
            SELECT
                id,
                organization_id,
                student_id,
                title,
                description,
                status,
                reviewed_by,
                reviewed_at,
                created_at
            FROM menu_proposals
            WHERE organization_id = ?
              AND status = ?
            ORDER BY created_at DESC, id DESC
            """,
            (resultSet, rowNumber) ->
                response(
                    resultSet,
                    false
                ),
            organizationId,
            status.name()
        );
    }

    private MenuProposalResponse locked(
        UUID organizationId,
        UUID proposalId
    ) {

        requireId(
            proposalId,
            "Menu proposal id"
        );

        MenuProposalResponse proposal =
            find(
                organizationId,
                proposalId,
                true
            );

        if (proposal == null) {
            throw new MenuVotingNotFoundException(
                "Menu proposal does not exist."
            );
        }

        return proposal;
    }

    private MenuProposalResponse find(
        UUID organizationId,
        UUID proposalId,
        boolean lock
    ) {

        String sql =
            """
            SELECT
                id,
                organization_id,
                student_id,
                title,
                description,
                status,
                reviewed_by,
                reviewed_at,
                created_at
            FROM menu_proposals
            WHERE id = ?
              AND organization_id = ?
            """;

        if (lock) {
            sql =
                sql + " FOR UPDATE";
        }

        List<MenuProposalResponse> rows =
            jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                    response(
                        resultSet,
                        false
                    ),
                proposalId,
                organizationId
            );

        if (rows.size() > 1) {
            throw new MenuVotingConflictException(
                "Multiple menu proposals matched one identifier."
            );
        }

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private MenuProposalResponse response(
        java.sql.ResultSet resultSet,
        boolean replayed
    ) throws java.sql.SQLException {

        return new MenuProposalResponse(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "organization_id",
                UUID.class
            ),
            resultSet.getObject(
                "student_id",
                UUID.class
            ),
            resultSet.getString(
                "title"
            ),
            resultSet.getString(
                "description"
            ),
            MenuProposalStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            ),
            resultSet.getObject(
                "reviewed_by",
                UUID.class
            ),
            resultSet.getObject(
                "reviewed_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "created_at",
                OffsetDateTime.class
            ),
            replayed
        );
    }

    private MenuProposalResponse replay(
        MenuProposalResponse stored
    ) {

        return new MenuProposalResponse(
            stored.id(),
            stored.organizationId(),
            stored.studentId(),
            stored.title(),
            stored.description(),
            stored.status(),
            stored.reviewedBy(),
            stored.reviewedAt(),
            stored.createdAt(),
            true
        );
    }

    private void validateCreate(
        CreateMenuProposalCommand command
    ) {

        if (command == null) {
            throw new MenuVotingValidationException(
                "Menu proposal payload is required."
            );
        }

        requiredText(
            command.title(),
            "Proposal title",
            180
        );

        nullableText(
            command.description()
        );
    }

    private void ownedStudent(
        UUID organizationId,
        UUID studentId
    ) {

        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM students s
                JOIN users u
                  ON u.id = s.user_id
                JOIN campuses c
                  ON c.id = s.campus_id
                WHERE s.id = ?
                  AND u.organization_id = ?
                  AND c.organization_id = ?
                """,
                Integer.class,
                studentId,
                organizationId,
                organizationId
            );

        if (
            count == null
            || count != 1
        ) {
            throw new MenuVotingNotFoundException(
                "Student does not exist in actor organization."
            );
        }
    }

    private UUID organizationId(
        UUID actorId
    ) {

        requireId(
            actorId,
            "Actor id"
        );

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT organization_id
                FROM users
                WHERE id = ?
                """,
                (resultSet, rowNumber) ->
                    resultSet.getObject(
                        "organization_id",
                        UUID.class
                    ),
                actorId
            );

        if (rows.size() != 1) {
            throw new BadCredentialsException(
                "Authenticated user does not exist."
            );
        }

        return rows.get(0);
    }

    private void requireId(
        UUID value,
        String label
    ) {

        if (value == null) {
            throw new MenuVotingValidationException(
                label + " is required."
            );
        }
    }

    private String requiredText(
        String value,
        String label,
        int maxLength
    ) {

        String normalized =
            value == null
                ? null
                : value.trim();

        if (
            normalized == null
            || normalized.isEmpty()
        ) {
            throw new MenuVotingValidationException(
                label + " is required."
            );
        }

        if (normalized.length() > maxLength) {
            throw new MenuVotingValidationException(
                label + " is too long."
            );
        }

        return normalized;
    }

    private String nullableText(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }
}