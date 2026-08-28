package com.sup2i.food.menuvote.domain;

import com.sup2i.food.catalog.domain.Product;
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

import java.util.UUID;

@Entity
@Table(
    name = "menu_vote_options",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_menu_vote_option_session",
            columnNames = {"id", "session_id"}
        )
    }
)
public class MenuVoteOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private MenuVoteSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "label", nullable = false, length = 180)
    private String label;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    protected MenuVoteOption() {
    }

    public MenuVoteOption(
        Product product,
        String label,
        String description,
        int displayOrder
    ) {
        this.product = product;
        this.label = label;
        this.description = description;
        this.displayOrder = displayOrder;
    }

    public UUID getId() {
        return id;
    }

    public MenuVoteSession getSession() {
        return session;
    }

    public void setSession(MenuVoteSession session) {
        this.session = session;
    }

    public Product getProduct() {
        return product;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}