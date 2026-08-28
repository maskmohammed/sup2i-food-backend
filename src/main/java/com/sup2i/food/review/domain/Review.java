package com.sup2i.food.review.domain;

import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.identity.domain.Student;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.order.domain.Order;
import com.sup2i.food.organization.domain.Organization;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "photos", columnDefinition = "jsonb")
    private String photos = "[]";

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    private ModerationStatus moderationStatus = ModerationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderated_by")
    private User moderatedBy;

    @Column(name = "moderated_at")
    private OffsetDateTime moderatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Review() {
    }

    public Review(
        Student student,
        Product product,
        Order order,
        int rating,
        String comment,
        List<String> photos
    ) {
        this.student = student;
        this.product = product;
        this.order = order;
        this.rating = rating;
        this.comment = comment;
        this.photos = photos == null ? "[]" : toPhotoJson(photos);
    }

    public UUID getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public Product getProduct() {
        return product;
    }

    public Order getOrder() {
        return order;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public String getPhotos() {
        return photos;
    }

    public ModerationStatus getModerationStatus() {
        return moderationStatus;
    }

    public User getModeratedBy() {
        return moderatedBy;
    }

    public OffsetDateTime getModeratedAt() {
        return moderatedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void moderate(
        ModerationStatus status,
        User moderator,
        OffsetDateTime at
    ) {
        this.moderationStatus = status;
        this.moderatedBy = moderator;
        this.moderatedAt = at;
    }

    public UUID getOrganizationId() {
        if (product != null) {
            return product.getOrganization().getId();
        }
        if (order != null) {
            return order.getOrganization().getId();
        }
        return student.getCampus().getOrganization().getId();
    }

    private static String toPhotoJson(List<String> photos) {
        try {
            return NEW_MAPPER.writeValueAsString(photos);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper NEW_MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();
}