package com.sup2i.food.voting;

import com.sup2i.food.voting.api.dto.CreateMenuProposalCommand;
import com.sup2i.food.voting.api.dto.CreateVoteCampaignCommand;
import com.sup2i.food.voting.api.dto.CreateVoteOptionCommand;
import com.sup2i.food.voting.api.dto.MenuProposalResponse;
import com.sup2i.food.voting.api.dto.VoteCampaignResponse;
import com.sup2i.food.voting.api.dto.VoteOptionResponse;
import com.sup2i.food.voting.api.dto.VoteResponse;
import com.sup2i.food.voting.domain.MenuProposalStatus;
import com.sup2i.food.voting.domain.MenuVoteCampaignStatus;
import com.sup2i.food.voting.exception.MenuVotingConflictException;
import com.sup2i.food.voting.exception.MenuVotingNotFoundException;
import com.sup2i.food.voting.service.MenuProposalService;
import com.sup2i.food.voting.service.MenuVotingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    properties = {
        "sup2i.security.jwt.issuer=sup2i-food-backend",
        "sup2i.security.jwt.secret-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "sup2i.security.mfa.encryption-key-base64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk="
    }
)
@ActiveProfiles("test")
@Testcontainers
class MenuVotingE2EIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(
            "postgres:17.10-bookworm"
        )
            .withDatabaseName(
                "sup2i_food_voting_test"
            );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MenuProposalService proposalService;

    @Autowired
    private MenuVotingService votingService;

    private Fixture fixture;

    @BeforeEach
    void setup() {

        fixture =
            createFixture(
                "MAIN"
            );
    }

    @Test
    void proposalReplayLifecycleAndTenantIsolationWork() {

        UUID proposalId =
            UUID.randomUUID();

        CreateMenuProposalCommand command =
            new CreateMenuProposalCommand(
                fixture.studentId(),
                "  Couscous étudiant  ",
                "  Proposition du campus  "
            );

        MenuProposalResponse first =
            proposalService.create(
                fixture.userId(),
                proposalId,
                command
            );

        MenuProposalResponse replay =
            proposalService.create(
                fixture.userId(),
                proposalId,
                command
            );

        assertThat(first.status())
            .isEqualTo(
                MenuProposalStatus.SUBMITTED
            );

        assertThat(first.title())
            .isEqualTo(
                "Couscous étudiant"
            );

        assertThat(replay.replayed())
            .isTrue();

        MenuProposalResponse reviewing =
            proposalService.beginReview(
                fixture.userId(),
                proposalId
            );

        assertThat(reviewing.status())
            .isEqualTo(
                MenuProposalStatus.UNDER_REVIEW
            );

        MenuProposalResponse accepted =
            proposalService.resolve(
                fixture.userId(),
                proposalId,
                MenuProposalStatus.ACCEPTED
            );

        assertThat(accepted.status())
            .isEqualTo(
                MenuProposalStatus.ACCEPTED
            );

        MenuProposalResponse archived =
            proposalService.archive(
                fixture.userId(),
                proposalId
            );

        assertThat(archived.status())
            .isEqualTo(
                MenuProposalStatus.ARCHIVED
            );

        Fixture foreign =
            createFixture(
                "FOREIGN-PROPOSAL"
            );

        assertThatThrownBy(() ->
            proposalService.get(
                foreign.userId(),
                proposalId
            )
        )
            .isInstanceOf(
                MenuVotingNotFoundException.class
            );

        assertThat(
            proposalCount(
                proposalId
            )
        ).isEqualTo(1L);
    }

    @Test
    void zeroAndOneSubjectOptionsAndReplayAfterActivationWork() {

        UUID campaignId =
            UUID.randomUUID();

        votingService.createCampaign(
            fixture.userId(),
            campaignId,
            currentCampaign(
                "Options",
                2
            )
        );

        UUID zeroSubjectId =
            UUID.randomUUID();

        CreateVoteOptionCommand zeroSubject =
            new CreateVoteOptionCommand(
                null,
                null,
                "Suggestion libre",
                "Option sans sujet",
                0
            );

        VoteOptionResponse zero =
            votingService.addOption(
                fixture.userId(),
                campaignId,
                zeroSubjectId,
                zeroSubject
            );

        assertThat(zero.productId())
            .isNull();

        assertThat(zero.menuProposalId())
            .isNull();

        UUID proposalId =
            UUID.randomUUID();

        proposalService.create(
            fixture.userId(),
            proposalId,
            new CreateMenuProposalCommand(
                fixture.studentId(),
                "Menu proposé",
                null
            )
        );

        UUID proposalOptionId =
            UUID.randomUUID();

        VoteOptionResponse one =
            votingService.addOption(
                fixture.userId(),
                campaignId,
                proposalOptionId,
                new CreateVoteOptionCommand(
                    null,
                    proposalId,
                    "Menu proposé",
                    null,
                    1
                )
            );

        assertThat(one.menuProposalId())
            .isEqualTo(
                proposalId
            );

        votingService.activateCampaign(
            fixture.userId(),
            campaignId
        );

        VoteOptionResponse replay =
            votingService.addOption(
                fixture.userId(),
                campaignId,
                zeroSubjectId,
                zeroSubject
            );

        assertThat(replay.replayed())
            .isTrue();

        assertThat(
            optionCount(
                zeroSubjectId
            )
        ).isEqualTo(1L);

        assertThatThrownBy(() ->
            votingService.addOption(
                fixture.userId(),
                campaignId,
                UUID.randomUUID(),
                new CreateVoteOptionCommand(
                    null,
                    null,
                    "Fresh after active",
                    null,
                    2
                )
            )
        )
            .isInstanceOf(
                MenuVotingConflictException.class
            );
    }

    @Test
    void voteReplayDuplicateSelectionAndMaxChoicesAreEnforced() {

        UUID campaignId =
            UUID.randomUUID();

        votingService.createCampaign(
            fixture.userId(),
            campaignId,
            currentCampaign(
                "Single choice",
                1
            )
        );

        UUID optionA =
            addFreeOption(
                campaignId,
                "A",
                0
            );

        UUID optionB =
            addFreeOption(
                campaignId,
                "B",
                1
            );

        votingService.activateCampaign(
            fixture.userId(),
            campaignId
        );

        UUID voteId =
            UUID.randomUUID();

        VoteResponse first =
            votingService.castVote(
                fixture.userId(),
                voteId,
                campaignId,
                optionA
            );

        VoteResponse replay =
            votingService.castVote(
                fixture.userId(),
                voteId,
                campaignId,
                optionA
            );

        assertThat(first.replayed())
            .isFalse();

        assertThat(replay.replayed())
            .isTrue();

        assertThatThrownBy(() ->
            votingService.castVote(
                fixture.userId(),
                UUID.randomUUID(),
                campaignId,
                optionA
            )
        )
            .isInstanceOf(
                MenuVotingConflictException.class
            );

        assertThatThrownBy(() ->
            votingService.castVote(
                fixture.userId(),
                UUID.randomUUID(),
                campaignId,
                optionB
            )
        )
            .isInstanceOf(
                MenuVotingConflictException.class
            )
            .hasMessageContaining(
                "max_choices"
            );

        assertThat(
            studentCampaignVoteCount(
                campaignId,
                fixture.studentId()
            )
        ).isEqualTo(1L);
    }

    @Test
    void votingWindowClosedStateAndCancellationAreEnforced() {

        OffsetDateTime now =
            OffsetDateTime.now();

        UUID futureCampaign =
            UUID.randomUUID();

        votingService.createCampaign(
            fixture.userId(),
            futureCampaign,
            new CreateVoteCampaignCommand(
                "Future-" + suffix(),
                null,
                now.plusHours(1),
                now.plusHours(2),
                1
            )
        );

        UUID futureOption =
            addFreeOption(
                futureCampaign,
                "Future",
                0
            );

        votingService.activateCampaign(
            fixture.userId(),
            futureCampaign
        );

        assertThatThrownBy(() ->
            votingService.castVote(
                fixture.userId(),
                UUID.randomUUID(),
                futureCampaign,
                futureOption
            )
        )
            .isInstanceOf(
                MenuVotingConflictException.class
            )
            .hasMessageContaining(
                "voting window"
            );

        UUID closedCampaign =
            UUID.randomUUID();

        votingService.createCampaign(
            fixture.userId(),
            closedCampaign,
            currentCampaign(
                "Closed",
                1
            )
        );

        UUID closedOption =
            addFreeOption(
                closedCampaign,
                "Closed",
                0
            );

        votingService.activateCampaign(
            fixture.userId(),
            closedCampaign
        );

        VoteCampaignResponse closed =
            votingService.closeCampaign(
                fixture.userId(),
                closedCampaign
            );

        assertThat(closed.status())
            .isEqualTo(
                MenuVoteCampaignStatus.CLOSED
            );

        assertThatThrownBy(() ->
            votingService.castVote(
                fixture.userId(),
                UUID.randomUUID(),
                closedCampaign,
                closedOption
            )
        )
            .isInstanceOf(
                MenuVotingConflictException.class
            );

        UUID cancelledCampaign =
            UUID.randomUUID();

        votingService.createCampaign(
            fixture.userId(),
            cancelledCampaign,
            currentCampaign(
                "Cancelled",
                1
            )
        );

        VoteCampaignResponse cancelled =
            votingService.cancelCampaign(
                fixture.userId(),
                cancelledCampaign
            );

        VoteCampaignResponse replay =
            votingService.cancelCampaign(
                fixture.userId(),
                cancelledCampaign
            );

        assertThat(cancelled.status())
            .isEqualTo(
                MenuVoteCampaignStatus.CANCELLED
            );

        assertThat(replay.status())
            .isEqualTo(
                MenuVoteCampaignStatus.CANCELLED
            );
    }

    @Test
    void proposalOptionCampaignAndVoteAreTenantIsolated() {

        Fixture foreign =
            createFixture(
                "FOREIGN-TENANT"
            );

        UUID foreignProposal =
            UUID.randomUUID();

        proposalService.create(
            foreign.userId(),
            foreignProposal,
            new CreateMenuProposalCommand(
                foreign.studentId(),
                "Foreign proposal",
                null
            )
        );

        UUID ownDraft =
            UUID.randomUUID();

        votingService.createCampaign(
            fixture.userId(),
            ownDraft,
            currentCampaign(
                "Own draft",
                1
            )
        );

        assertThatThrownBy(() ->
            votingService.addOption(
                fixture.userId(),
                ownDraft,
                UUID.randomUUID(),
                new CreateVoteOptionCommand(
                    null,
                    foreignProposal,
                    "Foreign proposal",
                    null,
                    0
                )
            )
        )
            .isInstanceOf(
                MenuVotingNotFoundException.class
            );

        UUID ownCampaign =
            UUID.randomUUID();

        votingService.createCampaign(
            fixture.userId(),
            ownCampaign,
            currentCampaign(
                "Own active",
                1
            )
        );

        UUID optionId =
            addFreeOption(
                ownCampaign,
                "Own option",
                0
            );

        votingService.activateCampaign(
            fixture.userId(),
            ownCampaign
        );

        assertThatThrownBy(() ->
            votingService.getCampaign(
                foreign.userId(),
                ownCampaign
            )
        )
            .isInstanceOf(
                MenuVotingNotFoundException.class
            );

        assertThatThrownBy(() ->
            votingService.castVote(
                foreign.userId(),
                UUID.randomUUID(),
                ownCampaign,
                optionId
            )
        )
            .isInstanceOf(
                MenuVotingNotFoundException.class
            );

        assertThat(
            studentCampaignVoteCount(
                ownCampaign,
                foreign.studentId()
            )
        ).isZero();
    }

    @Test
    void concurrentVotesRespectMaxChoicesExactlyOnce() throws Exception {

        UUID campaignId =
            UUID.randomUUID();

        votingService.createCampaign(
            fixture.userId(),
            campaignId,
            currentCampaign(
                "Concurrent",
                1
            )
        );

        UUID optionA =
            addFreeOption(
                campaignId,
                "Concurrent A",
                0
            );

        UUID optionB =
            addFreeOption(
                campaignId,
                "Concurrent B",
                1
            );

        votingService.activateCampaign(
            fixture.userId(),
            campaignId
        );

        ExecutorService executor =
            Executors.newFixedThreadPool(
                2
            );

        CountDownLatch start =
            new CountDownLatch(
                1
            );

        try {

            Future<Boolean> first =
                executor.submit(() -> {

                    start.await();

                    try {

                        votingService.castVote(
                            fixture.userId(),
                            UUID.randomUUID(),
                            campaignId,
                            optionA
                        );

                        return true;

                    } catch (
                        MenuVotingConflictException exception
                    ) {

                        return false;
                    }
                });

            Future<Boolean> second =
                executor.submit(() -> {

                    start.await();

                    try {

                        votingService.castVote(
                            fixture.userId(),
                            UUID.randomUUID(),
                            campaignId,
                            optionB
                        );

                        return true;

                    } catch (
                        MenuVotingConflictException exception
                    ) {

                        return false;
                    }
                });

            start.countDown();

            int successes =
                (first.get() ? 1 : 0)
                    + (second.get() ? 1 : 0);

            assertThat(successes)
                .isEqualTo(1);

            assertThat(
                studentCampaignVoteCount(
                    campaignId,
                    fixture.studentId()
                )
            ).isEqualTo(1L);

        } finally {

            executor.shutdownNow();
        }
    }

    private CreateVoteCampaignCommand currentCampaign(
        String title,
        int maxChoices
    ) {

        OffsetDateTime now =
            OffsetDateTime.now();

        return new CreateVoteCampaignCommand(
            title + "-" + suffix(),
            null,
            now.minusMinutes(5),
            now.plusHours(1),
            maxChoices
        );
    }

    private UUID addFreeOption(
        UUID campaignId,
        String label,
        int order
    ) {

        UUID optionId =
            UUID.randomUUID();

        votingService.addOption(
            fixture.userId(),
            campaignId,
            optionId,
            new CreateVoteOptionCommand(
                null,
                null,
                label,
                null,
                order
            )
        );

        return optionId;
    }

    private Fixture createFixture(
        String prefix
    ) {

        UUID organizationId =
            UUID.randomUUID();

        String orgCode =
            prefix +
            "-" +
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO organizations(
                id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, TRUE)
            """,
            organizationId,
            prefix + " Organization",
            orgCode
        );

        UUID userId =
            UUID.randomUUID();

        String userCode =
            suffix();

        /*
         * FINAL V053 SHAPE:
         * users.password_hash was dropped.
         */
        jdbcTemplate.update(
            """
            INSERT INTO users(
                id,
                organization_id,
                email,
                first_name,
                last_name,
                status
            )
            VALUES (
                ?, ?, ?, ?, ?,
                'ACTIVE'
            )
            """,
            userId,
            organizationId,
            "vote-" + userCode + "@sup2i.test",
            prefix,
            "User"
        );

        UUID campusId =
            UUID.randomUUID();

        String campusCode =
            prefix +
            "-" +
            suffix();

        jdbcTemplate.update(
            """
            INSERT INTO campuses(
                id,
                organization_id,
                name,
                code,
                is_active
            )
            VALUES (?, ?, ?, ?, TRUE)
            """,
            campusId,
            organizationId,
            prefix + " Campus",
            campusCode
        );

        UUID studentId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO students(
                id,
                user_id,
                campus_id,
                student_number,
                enrollment_status
            )
            VALUES (
                ?, ?, ?, ?,
                'ACTIVE'
            )
            """,
            studentId,
            userId,
            campusId,
            "STU-" + suffix()
        );

        return new Fixture(
            organizationId,
            userId,
            campusId,
            studentId
        );
    }

    private Long studentCampaignVoteCount(
        UUID campaignId,
        UUID studentId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM menu_votes
            WHERE campaign_id = ?
              AND student_id = ?
            """,
            Long.class,
            campaignId,
            studentId
        );
    }

    private Long proposalCount(
        UUID proposalId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM menu_proposals
            WHERE id = ?
            """,
            Long.class,
            proposalId
        );
    }

    private Long optionCount(
        UUID optionId
    ) {

        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM menu_vote_options
            WHERE id = ?
            """,
            Long.class,
            optionId
        );
    }

    private String suffix() {

        return UUID.randomUUID()
            .toString()
            .replace(
                "-",
                ""
            )
            .substring(
                0,
                12
            );
    }

    private record Fixture(
        UUID organizationId,
        UUID userId,
        UUID campusId,
        UUID studentId
    ) {
    }
}