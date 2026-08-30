package com.sup2i.food.loyalty.service;

import com.sup2i.food.loyalty.api.dto.LoyaltyResponse;
import com.sup2i.food.loyalty.api.dto.LoyaltyResponse.TransactionResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class LoyaltyService {

    private final JdbcTemplate jdbcTemplate;

    public LoyaltyService(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public LoyaltyResponse getMyLoyalty(
        UUID actorId
    ) {

        StudentContext student =
            studentContext(
                actorId
            );

        List<AccountContext> accounts =
            jdbcTemplate.query(
                """
                SELECT
                    la.id,
                    la.current_balance
                FROM loyalty_accounts la
                WHERE la.student_id = ?
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new AccountContext(
                        resultSet.getObject(
                            "id",
                            UUID.class
                        ),
                        resultSet.getInt(
                            "current_balance"
                        )
                    ),
                student.studentId()
            );

        if (accounts.isEmpty()) {

            return new LoyaltyResponse(
                0,
                List.of()
            );
        }

        if (accounts.size() != 1) {

            throw new IllegalStateException(
                "Loyalty account lookup returned multiple rows."
            );
        }

        AccountContext account =
            accounts.get(0);

        List<TransactionResponse> transactions =
            jdbcTemplate.query(
                """
                SELECT
                    type,
                    points,
                    reason,
                    created_at
                FROM loyalty_transactions
                WHERE account_id = ?
                ORDER BY
                    created_at DESC,
                    id DESC
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new TransactionResponse(
                        resultSet.getString(
                            "type"
                        ),
                        resultSet.getInt(
                            "points"
                        ),
                        resultSet.getString(
                            "reason"
                        ),
                        resultSet.getObject(
                            "created_at",
                            OffsetDateTime.class
                        )
                    ),
                account.accountId()
            );

        return new LoyaltyResponse(
            account.currentBalance(),
            List.copyOf(
                transactions
            )
        );
    }

    private StudentContext studentContext(
        UUID actorId
    ) {

        List<StudentContext> students =
            jdbcTemplate.query(
                """
                SELECT
                    s.id AS student_id
                FROM users u
                JOIN organizations o
                  ON o.id = u.organization_id
                JOIN students s
                  ON s.user_id = u.id
                WHERE u.id = ?
                  AND u.status = 'ACTIVE'
                  AND o.is_active = TRUE
                """,
                (
                    resultSet,
                    rowNumber
                ) ->
                    new StudentContext(
                        resultSet.getObject(
                            "student_id",
                            UUID.class
                        )
                    ),
                actorId
            );

        if (students.isEmpty()) {

            throw new BadCredentialsException(
                "Authenticated user is not an active student identity."
            );
        }

        if (students.size() != 1) {

            throw new IllegalStateException(
                "Student lookup returned multiple rows."
            );
        }

        return students.get(0);
    }

    private record StudentContext(
        UUID studentId
    ) {
    }

    private record AccountContext(
        UUID accountId,
        int currentBalance
    ) {
    }
}
