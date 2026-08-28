package com.sup2i.food.menuvote.domain;

import com.sup2i.food.identity.domain.User;
import com.sup2i.food.organization.domain.Organization;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "menu_vote_sessions")
public class MenuVoteSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "target_week", nullable = false)
    private LocalDate targetWeek;

    @Column(name = "vote_deadline", nullable = false)
    private OffsetDateTime voteDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MenuVoteStatus status = MenuVoteStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(
        mappedBy = "session",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderBy("displayOrder ASC")
    private List<MenuVoteOption> options = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MenuVoteSession() {
    }

    public MenuVoteSession(
        Organization organization,
        String title,
        LocalDate targetWeek,
        OffsetDateTime voteDeadline,
        User createdBy
    ) {
        this.organization = organization;
        this.title = title;
        this.targetWeek = targetWeek;
        this.voteDeadline = voteDeadline;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getTargetWeek() {
        return targetWeek;
    }

    public void setTargetWeek(LocalDate targetWeek) {
        this.targetWeek = targetWeek;
    }

    public OffsetDateTime getVoteDeadline() {
        return voteDeadline;
    }

    public void setVoteDeadline(OffsetDateTime voteDeadline) {
        this.voteDeadline = voteDeadline;
    }

    public MenuVoteStatus getStatus() {
        return status;
    }

    public void setStatus(MenuVoteStatus status) {
        this.status = status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public List<MenuVoteOption> getOptions() {
        return options;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void addOption(MenuVoteOption option) {
        options.add(option);
        option.setSession(this);
    }
}