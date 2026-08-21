package com.sup2i.food.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "menu_sections")
public class MenuSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "menu_id",
        nullable = false
    )
    private Menu menu;

    @Column(
        name = "code",
        length = 80
    )
    private String code;

    @Column(
        name = "name",
        nullable = false,
        length = 120
    )
    private String name;

    @Column(
        name = "min_select",
        nullable = false
    )
    private int minSelect = 1;

    @Column(
        name = "max_select",
        nullable = false
    )
    private int maxSelect = 1;

    @Column(
        name = "display_order",
        nullable = false
    )
    private int displayOrder;

    @Column(
        name = "is_active",
        nullable = false
    )
    private boolean active = true;

    protected MenuSection() {
    }

    public MenuSection(
        Menu menu,
        String code,
        String name,
        int minSelect,
        int maxSelect,
        int displayOrder,
        boolean active
    ) {
        this.menu = menu;
        this.code = code;
        this.name = name;
        this.minSelect = minSelect;
        this.maxSelect = maxSelect;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public Menu getMenu() {
        return menu;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getMinSelect() {
        return minSelect;
    }

    public int getMaxSelect() {
        return maxSelect;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }
}