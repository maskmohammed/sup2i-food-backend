package com.sup2i.food.menuvote.domain;

import com.sup2i.food.identity.domain.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "menu_votes",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_menu_vote_session_student",
            columnNames = {"session_id", "student_id"}
        )
    }
)
public class MenuVote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private MenuVoteSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private MenuVoteOption option;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected MenuVote() {
    }

    public MenuVote(
        MenuVoteSession session,
        MenuVoteOption option,
        Student student
    ) {
        this.session = session;
        this.option = option;
        this.student = student;
    }

    public UUID getId() {
        return id;
    }

    public MenuVoteSession getSession() {
        return session;
    }

    public MenuVoteOption getOption() {
        return option;
    }

    public Student getStudent() {
        return student;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}