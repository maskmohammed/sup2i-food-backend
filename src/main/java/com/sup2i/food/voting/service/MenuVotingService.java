package com.sup2i.food.voting.service;

import com.sup2i.food.voting.api.dto.CreateVoteCampaignCommand;
import com.sup2i.food.voting.api.dto.CreateVoteOptionCommand;
import com.sup2i.food.voting.api.dto.VoteCampaignResponse;
import com.sup2i.food.voting.api.dto.VoteOptionResponse;
import com.sup2i.food.voting.api.dto.VoteResponse;
import com.sup2i.food.voting.domain.MenuVoteCampaignStatus;
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
public class MenuVotingService {

    private final JdbcTemplate jdbcTemplate;

    public MenuVotingService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional
    public VoteCampaignResponse createCampaign(
        UUID actorId,
        UUID campaignId,
        CreateVoteCampaignCommand command
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        requireId(
            campaignId,
            "Campaign id"
        );

        validateCampaign(
            command
        );

        String title =
            requiredText(
                command.title(),
                "Campaign title",
                180
            );

        String description =
            nullableText(
                command.description()
            );

        VoteCampaignResponse existing =
            findCampaign(
                organizationId,
                campaignId,
                false
            );

        if (existing != null) {

            boolean same =
                Objects.equals(
                    existing.title(),
                    title
                )
                    && Objects.equals(
                        existing.description(),
                        description
                    )
                    && Objects.equals(
                        existing.startsAt(),
                        command.startsAt()
                    )
                    && Objects.equals(
                        existing.endsAt(),
                        command.endsAt()
                    )
                    && existing.maxChoices()
                        == command.maxChoices()
                    && Objects.equals(
                        existing.createdBy(),
                        actorId
                    );

            if (same) {
                return replayCampaign(
                    existing
                );
            }

            throw new MenuVotingConflictException(
                "Vote campaign identifier is already used by another payload."
            );
        }

        try {

            jdbcTemplate.update(
                """
                INSERT INTO menu_vote_campaigns(
                    id,
                    organization_id,
                    title,
                    description,
                    status,
                    starts_at,
                    ends_at,
                    max_choices,
                    created_by
                )
                VALUES (
                    ?, ?, ?, ?,
                    'DRAFT',
                    ?, ?, ?, ?
                )
                """,
                campaignId,
                organizationId,
                title,
                description,
                command.startsAt(),
                command.endsAt(),
                command.maxChoices(),
                actorId
            );

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new MenuVotingConflictException(
                "Vote campaign violates a schema invariant."
            );
        }

        return getCampaign(
            actorId,
            campaignId
        );
    }

    @Transactional
    public VoteOptionResponse addOption(
        UUID actorId,
        UUID campaignId,
        UUID optionId,
        CreateVoteOptionCommand command
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        requireId(
            campaignId,
            "Campaign id"
        );

        requireId(
            optionId,
            "Vote option id"
        );

        validateOption(
            command
        );

        String label =
            requiredText(
                command.label(),
                "Vote option label",
                180
            );

        String description =
            nullableText(
                command.description()
            );

        VoteOptionResponse existing =
            findOption(
                organizationId,
                campaignId,
                optionId,
                false
            );

        if (existing != null) {

            boolean same =
                Objects.equals(
                    existing.productId(),
                    command.productId()
                )
                    && Objects.equals(
                        existing.menuProposalId(),
                        command.menuProposalId()
                    )
                    && Objects.equals(
                        existing.label(),
                        label
                    )
                    && Objects.equals(
                        existing.description(),
                        description
                    )
                    && existing.displayOrder()
                        == command.displayOrder();

            if (same) {
                return replayOption(
                    existing
                );
            }

            throw new MenuVotingConflictException(
                "Vote option identifier is already used by another payload."
            );
        }

        CampaignContext campaign =
            lockedCampaignForUpdate(
                organizationId,
                campaignId
            );

        existing =
            findOption(
                organizationId,
                campaignId,
                optionId,
                false
            );

        if (existing != null) {

            boolean same =
                Objects.equals(
                    existing.productId(),
                    command.productId()
                )
                    && Objects.equals(
                        existing.menuProposalId(),
                        command.menuProposalId()
                    )
                    && Objects.equals(
                        existing.label(),
                        label
                    )
                    && Objects.equals(
                        existing.description(),
                        description
                    )
                    && existing.displayOrder()
                        == command.displayOrder();

            if (same) {
                return replayOption(
                    existing
                );
            }

            throw new MenuVotingConflictException(
                "Vote option identifier is already used by another payload."
            );
        }

        if (
            campaign.status()
                != MenuVoteCampaignStatus.DRAFT
        ) {
            throw new MenuVotingConflictException(
                "Vote options can only be modified while campaign is DRAFT."
            );
        }

        validateOptionSubjects(
            organizationId,
            command.productId(),
            command.menuProposalId()
        );

        try {

            jdbcTemplate.update(
                """
                INSERT INTO menu_vote_options(
                    id,
                    campaign_id,
                    product_id,
                    menu_proposal_id,
                    label,
                    description,
                    display_order
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                optionId,
                campaignId,
                command.productId(),
                command.menuProposalId(),
                label,
                description,
                command.displayOrder()
            );

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new MenuVotingConflictException(
                "Vote option violates campaign or subject invariants."
            );
        }

        return getOption(
            actorId,
            campaignId,
            optionId
        );
    }

    @Transactional
    public VoteCampaignResponse activateCampaign(
        UUID actorId,
        UUID campaignId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        CampaignContext campaign =
            lockedCampaignForUpdate(
                organizationId,
                campaignId
            );

        if (
            campaign.status()
                == MenuVoteCampaignStatus.ACTIVE
        ) {
            return getCampaign(
                actorId,
                campaignId
            );
        }

        if (
            campaign.status()
                != MenuVoteCampaignStatus.DRAFT
        ) {
            throw new MenuVotingConflictException(
                "Only a DRAFT campaign can become ACTIVE."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE menu_vote_campaigns
                SET status = 'ACTIVE'
                WHERE id = ?
                  AND organization_id = ?
                  AND status = 'DRAFT'
                """,
                campaignId,
                organizationId
            );

        if (updated != 1) {
            throw new MenuVotingConflictException(
                "Vote campaign changed concurrently."
            );
        }

        return getCampaign(
            actorId,
            campaignId
        );
    }

    @Transactional
    public VoteCampaignResponse closeCampaign(
        UUID actorId,
        UUID campaignId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        CampaignContext campaign =
            lockedCampaignForUpdate(
                organizationId,
                campaignId
            );

        if (
            campaign.status()
                == MenuVoteCampaignStatus.CLOSED
        ) {
            return getCampaign(
                actorId,
                campaignId
            );
        }

        if (
            campaign.status()
                != MenuVoteCampaignStatus.ACTIVE
        ) {
            throw new MenuVotingConflictException(
                "Only an ACTIVE campaign can be CLOSED."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE menu_vote_campaigns
                SET status = 'CLOSED'
                WHERE id = ?
                  AND organization_id = ?
                  AND status = 'ACTIVE'
                """,
                campaignId,
                organizationId
            );

        if (updated != 1) {
            throw new MenuVotingConflictException(
                "Vote campaign changed concurrently."
            );
        }

        return getCampaign(
            actorId,
            campaignId
        );
    }

    @Transactional
    public VoteCampaignResponse cancelCampaign(
        UUID actorId,
        UUID campaignId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        CampaignContext campaign =
            lockedCampaignForUpdate(
                organizationId,
                campaignId
            );

        if (
            campaign.status()
                == MenuVoteCampaignStatus.CANCELLED
        ) {
            return getCampaign(
                actorId,
                campaignId
            );
        }

        boolean cancellable =
            campaign.status()
                == MenuVoteCampaignStatus.DRAFT
                || campaign.status()
                    == MenuVoteCampaignStatus.ACTIVE;

        if (!cancellable) {
            throw new MenuVotingConflictException(
                "Closed campaign cannot be cancelled."
            );
        }

        int updated =
            jdbcTemplate.update(
                """
                UPDATE menu_vote_campaigns
                SET status = 'CANCELLED'
                WHERE id = ?
                  AND organization_id = ?
                  AND status IN ('DRAFT','ACTIVE')
                """,
                campaignId,
                organizationId
            );

        if (updated != 1) {
            throw new MenuVotingConflictException(
                "Vote campaign changed concurrently."
            );
        }

        return getCampaign(
            actorId,
            campaignId
        );
    }

    @Transactional
    public VoteResponse castVote(
        UUID actorId,
        UUID voteId,
        UUID campaignId,
        UUID optionId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        requireId(
            voteId,
            "Vote id"
        );

        requireId(
            campaignId,
            "Campaign id"
        );

        requireId(
            optionId,
            "Option id"
        );

        StudentContext student =
            lockedStudentForActor(
                actorId,
                organizationId
            );

        VoteResponse existing =
            findVoteById(
                organizationId,
                voteId
            );

        if (existing != null) {

            boolean same =
                existing.campaignId()
                    .equals(
                        campaignId
                    )
                    && existing.optionId()
                        .equals(
                            optionId
                        )
                    && existing.studentId()
                        .equals(
                            student.id()
                        );

            if (same) {
                return replayVote(
                    existing
                );
            }

            throw new MenuVotingConflictException(
                "Vote identifier is already used by another payload."
            );
        }

        CampaignContext campaign =
            lockedCampaignForVote(
                organizationId,
                campaignId
            );

        if (
            campaign.status()
                != MenuVoteCampaignStatus.ACTIVE
        ) {
            throw new MenuVotingConflictException(
                "Votes are only accepted for ACTIVE campaigns."
            );
        }

        boolean started =
            !campaign.databaseNow()
                .isBefore(
                    campaign.startsAt()
                );

        boolean beforeEnd =
            campaign.databaseNow()
                .isBefore(
                    campaign.endsAt()
                );

        if (
            !started
            || !beforeEnd
        ) {
            throw new MenuVotingConflictException(
                "Vote campaign is outside its voting window."
            );
        }

        VoteOptionResponse option =
            findOption(
                organizationId,
                campaignId,
                optionId,
                false
            );

        if (option == null) {
            throw new MenuVotingNotFoundException(
                "Vote option does not exist in selected campaign."
            );
        }

        List<VoteResponse> existingSelection =
            jdbcTemplate.query(
                """
                SELECT
                    vote.id,
                    vote.campaign_id,
                    vote.option_id,
                    vote.student_id,
                    vote.created_at
                FROM menu_votes vote
                JOIN menu_vote_campaigns campaign
                  ON campaign.id = vote.campaign_id
                WHERE vote.campaign_id = ?
                  AND vote.option_id = ?
                  AND vote.student_id = ?
                  AND campaign.organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    voteResponse(
                        resultSet,
                        false
                    ),
                campaignId,
                optionId,
                student.id(),
                organizationId
            );

        if (!existingSelection.isEmpty()) {
            throw new MenuVotingConflictException(
                "Student already selected this vote option."
            );
        }

        Integer selectedChoices =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM menu_votes
                WHERE campaign_id = ?
                  AND student_id = ?
                """,
                Integer.class,
                campaignId,
                student.id()
            );

        int currentChoices =
            selectedChoices == null
                ? 0
                : selectedChoices;

        if (
            currentChoices
                >= campaign.maxChoices()
        ) {
            throw new MenuVotingConflictException(
                "Campaign max_choices limit has been reached for this student."
            );
        }

        try {

            jdbcTemplate.update(
                """
                INSERT INTO menu_votes(
                    id,
                    campaign_id,
                    option_id,
                    student_id
                )
                VALUES (?, ?, ?, ?)
                """,
                voteId,
                campaignId,
                optionId,
                student.id()
            );

        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new MenuVotingConflictException(
                "Vote conflicts with an existing student selection."
            );
        }

        VoteResponse created =
            findVoteById(
                organizationId,
                voteId
            );

        if (created == null) {
            throw new MenuVotingConflictException(
                "Vote was not persisted."
            );
        }

        return created;
    }

    @Transactional(readOnly = true)
    public VoteCampaignResponse getCampaign(
        UUID actorId,
        UUID campaignId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        requireId(
            campaignId,
            "Campaign id"
        );

        VoteCampaignResponse response =
            findCampaign(
                organizationId,
                campaignId,
                false
            );

        if (response == null) {
            throw new MenuVotingNotFoundException(
                "Vote campaign does not exist."
            );
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<VoteCampaignResponse> listCampaigns(
        UUID actorId,
        MenuVoteCampaignStatus status
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
                    title,
                    description,
                    status,
                    starts_at,
                    ends_at,
                    max_choices,
                    created_by
                FROM menu_vote_campaigns
                WHERE organization_id = ?
                ORDER BY starts_at DESC, id DESC
                """,
                (resultSet, rowNumber) ->
                    campaignResponse(
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
                title,
                description,
                status,
                starts_at,
                ends_at,
                max_choices,
                created_by
            FROM menu_vote_campaigns
            WHERE organization_id = ?
              AND status = ?
            ORDER BY starts_at DESC, id DESC
            """,
            (resultSet, rowNumber) ->
                campaignResponse(
                    resultSet,
                    false
                ),
            organizationId,
            status.name()
        );
    }

    @Transactional(readOnly = true)
    public VoteOptionResponse getOption(
        UUID actorId,
        UUID campaignId,
        UUID optionId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        VoteOptionResponse option =
            findOption(
                organizationId,
                campaignId,
                optionId,
                false
            );

        if (option == null) {
            throw new MenuVotingNotFoundException(
                "Vote option does not exist."
            );
        }

        return option;
    }

    @Transactional(readOnly = true)
    public List<VoteOptionResponse> listOptions(
        UUID actorId,
        UUID campaignId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        if (
            findCampaign(
                organizationId,
                campaignId,
                false
            ) == null
        ) {
            throw new MenuVotingNotFoundException(
                "Vote campaign does not exist."
            );
        }

        return jdbcTemplate.query(
            """
            SELECT
                option.id,
                option.campaign_id,
                option.product_id,
                option.menu_proposal_id,
                option.label,
                option.description,
                option.display_order,
                COUNT(vote.id) AS vote_count
            FROM menu_vote_options option
            JOIN menu_vote_campaigns campaign
              ON campaign.id = option.campaign_id
            LEFT JOIN menu_votes vote
              ON vote.option_id = option.id
             AND vote.campaign_id = option.campaign_id
            WHERE option.campaign_id = ?
              AND campaign.organization_id = ?
            GROUP BY
                option.id,
                option.campaign_id,
                option.product_id,
                option.menu_proposal_id,
                option.label,
                option.description,
                option.display_order
            ORDER BY
                option.display_order ASC,
                option.label ASC,
                option.id ASC
            """,
            (resultSet, rowNumber) ->
                optionResponse(
                    resultSet,
                    false
                ),
            campaignId,
            organizationId
        );
    }

    @Transactional(readOnly = true)
    public List<VoteResponse> listMyVotes(
        UUID actorId,
        UUID campaignId
    ) {

        UUID organizationId =
            organizationId(
                actorId
            );

        UUID studentId =
            studentIdForActor(
                actorId,
                organizationId
            );

        return jdbcTemplate.query(
            """
            SELECT
                vote.id,
                vote.campaign_id,
                vote.option_id,
                vote.student_id,
                vote.created_at
            FROM menu_votes vote
            JOIN menu_vote_campaigns campaign
              ON campaign.id = vote.campaign_id
            WHERE vote.campaign_id = ?
              AND vote.student_id = ?
              AND campaign.organization_id = ?
            ORDER BY vote.created_at ASC, vote.id ASC
            """,
            (resultSet, rowNumber) ->
                voteResponse(
                    resultSet,
                    false
                ),
            campaignId,
            studentId,
            organizationId
        );
    }

    private CampaignContext lockedCampaignForUpdate(
        UUID organizationId,
        UUID campaignId
    ) {

        List<CampaignContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    status,
                    starts_at,
                    ends_at,
                    max_choices,
                    CURRENT_TIMESTAMP AS database_now
                FROM menu_vote_campaigns
                WHERE id = ?
                  AND organization_id = ?
                FOR UPDATE
                """,
                (resultSet, rowNumber) ->
                    campaignContext(
                        resultSet
                    ),
                campaignId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new MenuVotingNotFoundException(
                "Vote campaign does not exist."
            );
        }

        return rows.get(0);
    }

    private CampaignContext lockedCampaignForVote(
        UUID organizationId,
        UUID campaignId
    ) {

        List<CampaignContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    status,
                    starts_at,
                    ends_at,
                    max_choices,
                    CURRENT_TIMESTAMP AS database_now
                FROM menu_vote_campaigns
                WHERE id = ?
                  AND organization_id = ?
                FOR SHARE
                """,
                (resultSet, rowNumber) ->
                    campaignContext(
                        resultSet
                    ),
                campaignId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new MenuVotingNotFoundException(
                "Vote campaign does not exist."
            );
        }

        return rows.get(0);
    }

    private StudentContext lockedStudentForActor(
        UUID actorId,
        UUID organizationId
    ) {

        List<StudentContext> rows =
            jdbcTemplate.query(
                """
                SELECT
                    s.id,
                    s.campus_id
                FROM students s
                JOIN users u
                  ON u.id = s.user_id
                JOIN campuses c
                  ON c.id = s.campus_id
                WHERE s.user_id = ?
                  AND u.organization_id = ?
                  AND c.organization_id = ?
                FOR UPDATE OF s
                """,
                (resultSet, rowNumber) ->
                    new StudentContext(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getObject(
                            "campus_id",
                            UUID.class
                        )
                    ),
                actorId,
                organizationId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new MenuVotingNotFoundException(
                "Authenticated user has no student profile in actor organization."
            );
        }

        return rows.get(0);
    }

    private UUID studentIdForActor(
        UUID actorId,
        UUID organizationId
    ) {

        List<UUID> rows =
            jdbcTemplate.query(
                """
                SELECT s.id
                FROM students s
                JOIN users u
                  ON u.id = s.user_id
                JOIN campuses c
                  ON c.id = s.campus_id
                WHERE s.user_id = ?
                  AND u.organization_id = ?
                  AND c.organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    resultSet.getObject(
                        "id",
                        UUID.class
                    ),
                actorId,
                organizationId,
                organizationId
            );

        if (rows.size() != 1) {
            throw new MenuVotingNotFoundException(
                "Authenticated user has no student profile in actor organization."
            );
        }

        return rows.get(0);
    }

    private VoteCampaignResponse findCampaign(
        UUID organizationId,
        UUID campaignId,
        boolean replayed
    ) {

        requireId(
            campaignId,
            "Campaign id"
        );

        List<VoteCampaignResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    id,
                    organization_id,
                    title,
                    description,
                    status,
                    starts_at,
                    ends_at,
                    max_choices,
                    created_by
                FROM menu_vote_campaigns
                WHERE id = ?
                  AND organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    campaignResponse(
                        resultSet,
                        replayed
                    ),
                campaignId,
                organizationId
            );

        if (rows.size() > 1) {
            throw new MenuVotingConflictException(
                "Multiple vote campaigns matched one identifier."
            );
        }

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private VoteOptionResponse findOption(
        UUID organizationId,
        UUID campaignId,
        UUID optionId,
        boolean replayed
    ) {

        requireId(
            campaignId,
            "Campaign id"
        );

        requireId(
            optionId,
            "Option id"
        );

        List<VoteOptionResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    option.id,
                    option.campaign_id,
                    option.product_id,
                    option.menu_proposal_id,
                    option.label,
                    option.description,
                    option.display_order,
                    COUNT(vote.id) AS vote_count
                FROM menu_vote_options option
                JOIN menu_vote_campaigns campaign
                  ON campaign.id = option.campaign_id
                LEFT JOIN menu_votes vote
                  ON vote.option_id = option.id
                 AND vote.campaign_id = option.campaign_id
                WHERE option.id = ?
                  AND option.campaign_id = ?
                  AND campaign.organization_id = ?
                GROUP BY
                    option.id,
                    option.campaign_id,
                    option.product_id,
                    option.menu_proposal_id,
                    option.label,
                    option.description,
                    option.display_order
                """,
                (resultSet, rowNumber) ->
                    optionResponse(
                        resultSet,
                        replayed
                    ),
                optionId,
                campaignId,
                organizationId
            );

        if (rows.size() > 1) {
            throw new MenuVotingConflictException(
                "Multiple vote options matched one identifier."
            );
        }

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private VoteResponse findVoteById(
        UUID organizationId,
        UUID voteId
    ) {

        List<VoteResponse> rows =
            jdbcTemplate.query(
                """
                SELECT
                    vote.id,
                    vote.campaign_id,
                    vote.option_id,
                    vote.student_id,
                    vote.created_at
                FROM menu_votes vote
                JOIN menu_vote_campaigns campaign
                  ON campaign.id = vote.campaign_id
                WHERE vote.id = ?
                  AND campaign.organization_id = ?
                """,
                (resultSet, rowNumber) ->
                    voteResponse(
                        resultSet,
                        false
                    ),
                voteId,
                organizationId
            );

        if (rows.size() > 1) {
            throw new MenuVotingConflictException(
                "Multiple votes matched one identifier."
            );
        }

        return rows.isEmpty()
            ? null
            : rows.get(0);
    }

    private void validateOptionSubjects(
        UUID organizationId,
        UUID productId,
        UUID proposalId
    ) {

        if (
            productId != null
            && proposalId != null
        ) {
            throw new MenuVotingValidationException(
                "Vote option may reference at most one subject."
            );
        }

        /*
         * V036 intentionally uses num_nonnulls(...) <= 1.
         * Therefore both subjects being null is schema-valid and
         * remains application-valid. Do not strengthen to exactly one.
         */

        if (productId != null) {

            Integer productCount =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM products
                    WHERE id = ?
                      AND organization_id = ?
                    """,
                    Integer.class,
                    productId,
                    organizationId
                );

            if (
                productCount == null
                || productCount != 1
            ) {
                throw new MenuVotingNotFoundException(
                    "Vote option product does not exist in actor organization."
                );
            }
        }

        if (proposalId != null) {

            Integer proposalCount =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM menu_proposals
                    WHERE id = ?
                      AND organization_id = ?
                    """,
                    Integer.class,
                    proposalId,
                    organizationId
                );

            if (
                proposalCount == null
                || proposalCount != 1
            ) {
                throw new MenuVotingNotFoundException(
                    "Vote option proposal does not exist in actor organization."
                );
            }
        }
    }

    private VoteCampaignResponse campaignResponse(
        java.sql.ResultSet resultSet,
        boolean replayed
    ) throws java.sql.SQLException {

        return new VoteCampaignResponse(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "organization_id",
                UUID.class
            ),
            resultSet.getString(
                "title"
            ),
            resultSet.getString(
                "description"
            ),
            MenuVoteCampaignStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            ),
            resultSet.getObject(
                "starts_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "ends_at",
                OffsetDateTime.class
            ),
            resultSet.getInt(
                "max_choices"
            ),
            resultSet.getObject(
                "created_by",
                UUID.class
            ),
            replayed
        );
    }

    private VoteOptionResponse optionResponse(
        java.sql.ResultSet resultSet,
        boolean replayed
    ) throws java.sql.SQLException {

        return new VoteOptionResponse(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "campaign_id",
                UUID.class
            ),
            resultSet.getObject(
                "product_id",
                UUID.class
            ),
            resultSet.getObject(
                "menu_proposal_id",
                UUID.class
            ),
            resultSet.getString(
                "label"
            ),
            resultSet.getString(
                "description"
            ),
            resultSet.getInt(
                "display_order"
            ),
            resultSet.getLong(
                "vote_count"
            ),
            replayed
        );
    }

    private VoteResponse voteResponse(
        java.sql.ResultSet resultSet,
        boolean replayed
    ) throws java.sql.SQLException {

        return new VoteResponse(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "campaign_id",
                UUID.class
            ),
            resultSet.getObject(
                "option_id",
                UUID.class
            ),
            resultSet.getObject(
                "student_id",
                UUID.class
            ),
            resultSet.getObject(
                "created_at",
                OffsetDateTime.class
            ),
            replayed
        );
    }

    private CampaignContext campaignContext(
        java.sql.ResultSet resultSet
    ) throws java.sql.SQLException {

        return new CampaignContext(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            MenuVoteCampaignStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            ),
            resultSet.getObject(
                "starts_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "ends_at",
                OffsetDateTime.class
            ),
            resultSet.getInt(
                "max_choices"
            ),
            resultSet.getObject(
                "database_now",
                OffsetDateTime.class
            )
        );
    }

    private VoteCampaignResponse replayCampaign(
        VoteCampaignResponse stored
    ) {

        return new VoteCampaignResponse(
            stored.id(),
            stored.organizationId(),
            stored.title(),
            stored.description(),
            stored.status(),
            stored.startsAt(),
            stored.endsAt(),
            stored.maxChoices(),
            stored.createdBy(),
            true
        );
    }

    private VoteOptionResponse replayOption(
        VoteOptionResponse stored
    ) {

        return new VoteOptionResponse(
            stored.id(),
            stored.campaignId(),
            stored.productId(),
            stored.menuProposalId(),
            stored.label(),
            stored.description(),
            stored.displayOrder(),
            stored.voteCount(),
            true
        );
    }

    private VoteResponse replayVote(
        VoteResponse stored
    ) {

        return new VoteResponse(
            stored.id(),
            stored.campaignId(),
            stored.optionId(),
            stored.studentId(),
            stored.createdAt(),
            true
        );
    }

    private void validateCampaign(
        CreateVoteCampaignCommand command
    ) {

        if (command == null) {
            throw new MenuVotingValidationException(
                "Vote campaign payload is required."
            );
        }

        requiredText(
            command.title(),
            "Campaign title",
            180
        );

        if (command.startsAt() == null) {
            throw new MenuVotingValidationException(
                "Campaign startsAt is required."
            );
        }

        if (command.endsAt() == null) {
            throw new MenuVotingValidationException(
                "Campaign endsAt is required."
            );
        }

        if (
            !command.endsAt()
                .isAfter(
                    command.startsAt()
                )
        ) {
            throw new MenuVotingValidationException(
                "Campaign endsAt must be after startsAt."
            );
        }

        if (command.maxChoices() <= 0) {
            throw new MenuVotingValidationException(
                "Campaign maxChoices must be greater than zero."
            );
        }
    }

    private void validateOption(
        CreateVoteOptionCommand command
    ) {

        if (command == null) {
            throw new MenuVotingValidationException(
                "Vote option payload is required."
            );
        }

        requiredText(
            command.label(),
            "Vote option label",
            180
        );

        if (command.displayOrder() < 0) {
            throw new MenuVotingValidationException(
                "Vote option displayOrder cannot be negative."
            );
        }

        if (
            command.productId() != null
            && command.menuProposalId() != null
        ) {
            throw new MenuVotingValidationException(
                "Vote option may reference at most one subject."
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

    private record CampaignContext(
        UUID id,
        MenuVoteCampaignStatus status,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        int maxChoices,
        OffsetDateTime databaseNow
    ) {
    }

    private record StudentContext(
        UUID id,
        UUID campusId
    ) {
    }
}