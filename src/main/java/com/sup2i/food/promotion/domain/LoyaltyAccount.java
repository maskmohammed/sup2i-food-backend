package com.sup2i.food.promotion.domain;

import com.sup2i.food.identity.domain.Student;
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
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loyalty_accounts")
public class LoyaltyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LoyaltyAccountStatus status = LoyaltyAccountStatus.ACTIVE;

    @Column(name = "current_balance", nullable = false)
    private int currentBalance = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "lifetime_earned", nullable = false)
    private int lifetimeEarned = 0;

    @Column(name = "lifetime_redeemed", nullable = false)
    private int lifetimeRedeemed = 0;

    protected LoyaltyAccount() {
    }

    public LoyaltyAccount(Student student) {
        this.student = student;
    }

    public UUID getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public LoyaltyAccountStatus getStatus() {
        return status;
    }

    public int getCurrentBalance() {
        return currentBalance;
    }

    public int getLifetimeEarned() {
        return lifetimeEarned;
    }

    public int getLifetimeRedeemed() {
        return lifetimeRedeemed;
    }

    public void earn(int points) {
        this.currentBalance += points;
        this.lifetimeEarned += points;
    }

    public void redeem(int points) {
        this.currentBalance -= points;
        this.lifetimeRedeemed += points;
    }

    public void adjust(int points) {
        this.currentBalance += points;
        if (points > 0) {
            this.lifetimeEarned += points;
        }
    }
}