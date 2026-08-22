package com.sup2i.food.catalog.service;

import com.sup2i.food.catalog.api.dto.CreateMenuItemRequest;
import com.sup2i.food.catalog.api.dto.CreateMenuSectionRequest;
import com.sup2i.food.catalog.api.dto.MenuItemResponse;
import com.sup2i.food.catalog.api.dto.MenuResponse;
import com.sup2i.food.catalog.api.dto.MenuSectionResponse;
import com.sup2i.food.catalog.api.dto.UpsertMenuRequest;
import com.sup2i.food.catalog.api.dto.UpdateMenuItemRequest;
import com.sup2i.food.catalog.api.dto.UpdateMenuSectionRequest;
import com.sup2i.food.catalog.domain.Menu;
import com.sup2i.food.catalog.domain.MenuItem;
import com.sup2i.food.catalog.domain.MenuPricingMode;
import com.sup2i.food.catalog.domain.MenuSection;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.catalog.exception.CatalogConflictException;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;
import com.sup2i.food.catalog.exception.ProductUnavailableException;
import com.sup2i.food.catalog.repository.MenuItemRepository;
import com.sup2i.food.catalog.repository.MenuRepository;
import com.sup2i.food.catalog.repository.MenuSectionRepository;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.catalog.repository.ProductVariantRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CatalogMenuService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final MenuRepository menuRepository;
    private final MenuSectionRepository sectionRepository;
    private final MenuItemRepository itemRepository;

    public CatalogMenuService(
        UserRepository userRepository,
        ProductRepository productRepository,
        ProductVariantRepository variantRepository,
        MenuRepository menuRepository,
        MenuSectionRepository sectionRepository,
        MenuItemRepository itemRepository
    ) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.menuRepository = menuRepository;
        this.sectionRepository = sectionRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional
    public MenuResponse upsertMenu(
        UUID actorId,
        UUID productId,
        UpsertMenuRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        Product product =
            productForOrganization(
                productId,
                actor.getOrganization().getId()
            );

        Menu menu =
            menuRepository
                .findByProduct_Id(
                    productId
                )
                .orElseGet(() ->
                    new Menu(product)
                );

        menu.setPricingMode(
            request.pricingMode() == null
                ? MenuPricingMode.FIXED
                : request.pricingMode()
        );

        menu.setDescription(
            normalizeNullableText(
                request.description()
            )
        );

        menu.setActive(
            request.active() == null
                || request.active()
        );

        try {
            menu =
                menuRepository
                    .saveAndFlush(menu);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Menu conflicts with an existing resource."
            );
        }

        return adminMenuResponse(
            menu
        );
    }

    @Transactional
    public MenuSectionResponse createSection(
        UUID actorId,
        UUID productId,
        CreateMenuSectionRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        Product product =
            productForOrganization(
                productId,
                actor.getOrganization().getId()
            );

        Menu menu =
            menuForProduct(
                product.getId()
            );

        int minSelect =
            request.minSelect() == null
                ? 1
                : request.minSelect();

        int maxSelect =
            request.maxSelect() == null
                ? 1
                : request.maxSelect();

        MenuSection section =
            new MenuSection(
                menu,
                normalizeNullableText(
                    request.code()
                ),
                request.name().trim(),
                minSelect,
                maxSelect,
                request.displayOrder(),
                request.active() == null
                    || request.active()
            );

        try {
            section =
                sectionRepository
                    .saveAndFlush(section);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Menu section conflicts with an existing resource."
            );
        }

        return new MenuSectionResponse(
            section.getId(),
            section.getCode(),
            section.getName(),
            section.getMinSelect(),
            section.getMaxSelect(),
            section.getDisplayOrder(),
            section.isActive(),
            List.of()
        );
    }

    @Transactional
    public MenuItemResponse createItem(
        UUID actorId,
        UUID menuProductId,
        UUID sectionId,
        CreateMenuItemRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization().getId();

        Product menuProduct =
            productForOrganization(
                menuProductId,
                organizationId
            );

        Menu menu =
            menuForProduct(
                menuProduct.getId()
            );

        MenuSection section =
            sectionRepository
                .findByIdAndMenu_Id(
                    sectionId,
                    menu.getId()
                )
                .orElseThrow(() ->
                    new CatalogNotFoundException(
                        "Menu section does not exist."
                    )
                );

        Product itemProduct =
            productForOrganization(
                request.productId(),
                organizationId
            );

        ProductVariant variant = null;

        if (request.variantId() != null) {
            variant =
                variantRepository
                    .findByIdAndProduct_Id(
                        request.variantId(),
                        itemProduct.getId()
                    )
                    .orElseThrow(() ->
                        new CatalogNotFoundException(
                            "Variant does not belong to the selected product."
                        )
                    );
        }

        MenuItem item =
            new MenuItem(
                section,
                itemProduct,
                variant,
                request.quantity() == null
                    ? BigDecimal.ONE
                    : request.quantity(),
                request.priceDelta() == null
                    ? BigDecimal.ZERO
                    : request.priceDelta(),
                request.defaultItem() != null
                    && request.defaultItem(),
                request.active() == null
                    || request.active(),
                request.displayOrder()
            );

        try {
            item =
                itemRepository
                    .saveAndFlush(item);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Menu item conflicts with an existing resource."
            );
        }

        return toItemResponse(item);
    }

    @Transactional
    public MenuSectionResponse updateSection(
        UUID actorId,
        UUID productId,
        UUID sectionId,
        UpdateMenuSectionRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        Product product =
            productForOrganization(
                productId,
                actor.getOrganization().getId()
            );

        Menu menu =
            menuForProduct(
                product.getId()
            );

        MenuSection section =
            sectionRepository
                .findByIdAndMenu_Id(
                    sectionId,
                    menu.getId()
                )
                .orElseThrow(() ->
                    new CatalogNotFoundException(
                        "Menu section does not exist."
                    )
                );

        int minSelect =
            request.minSelect() == null
                ? section.getMinSelect()
                : request.minSelect();

        int maxSelect =
            request.maxSelect() == null
                ? section.getMaxSelect()
                : request.maxSelect();

        if (minSelect > maxSelect) {
            throw new com.sup2i.food.catalog.exception.CatalogValidationException(
                "minSelect must be less than or equal to maxSelect"
            );
        }

        section.update(
            normalizeNullableText(
                request.code()
            ),
            request.name().trim(),
            minSelect,
            maxSelect,
            request.displayOrder(),
            request.active() == null
                ? section.isActive()
                : request.active()
        );

        try {
            section =
                sectionRepository
                    .saveAndFlush(section);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Menu section conflicts with an existing resource."
            );
        }

        return toSectionResponse(
            section,
            true
        );
    }

    @Transactional
    public MenuItemResponse updateItem(
        UUID actorId,
        UUID productId,
        UUID sectionId,
        UUID itemId,
        UpdateMenuItemRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization().getId();

        Product menuProduct =
            productForOrganization(
                productId,
                organizationId
            );

        Menu menu =
            menuForProduct(
                menuProduct.getId()
            );

        MenuSection section =
            sectionRepository
                .findByIdAndMenu_Id(
                    sectionId,
                    menu.getId()
                )
                .orElseThrow(() ->
                    new CatalogNotFoundException(
                        "Menu section does not exist."
                    )
                );

        MenuItem item =
            itemRepository
                .findByIdAndMenuSection_Id(
                    itemId,
                    section.getId()
                )
                .orElseThrow(() ->
                    new CatalogNotFoundException(
                        "Menu item does not exist."
                    )
                );

        Product itemProduct =
            productForOrganization(
                request.productId(),
                organizationId
            );

        ProductVariant variant = null;

        if (request.variantId() != null) {
            variant =
                variantRepository
                    .findByIdAndProduct_Id(
                        request.variantId(),
                        itemProduct.getId()
                    )
                    .orElseThrow(() ->
                        new CatalogNotFoundException(
                            "Variant does not belong to the selected product."
                        )
                    );
        }

        item.update(
            itemProduct,
            variant,
            request.quantity(),
            request.priceDelta(),
            request.defaultItem(),
            request.active(),
            request.displayOrder()
        );

        try {
            item =
                itemRepository
                    .saveAndFlush(item);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Menu item conflicts with an existing resource."
            );
        }

        return toItemResponse(item);
    }
    @Transactional(readOnly = true)
    public MenuResponse menu(
        UUID userId,
        UUID productId
    ) {

        User user =
            authenticatedUser(userId);

        UUID organizationId =
            user.getOrganization().getId();

        Product product =
            productForOrganization(
                productId,
                organizationId
            );

        if (
            !product.isActive()
            || !product.getCategory().isActive()
        ) {
            throw new ProductUnavailableException(
                "Product is not available."
            );
        }

        Menu menu =
            menuForProduct(
                productId
            );

        if (!menu.isActive()) {
            throw new ProductUnavailableException(
                "Menu is not available."
            );
        }

        List<MenuSection> sections =
            sectionRepository
                .findAllByMenu_IdAndActiveTrueOrderByDisplayOrderAscNameAsc(
                    menu.getId()
                );

        List<UUID> sectionIds =
            sections.stream()
                .map(
                    MenuSection::getId
                )
                .toList();

        List<MenuItem> items =
            sectionIds.isEmpty()
                ? List.of()
                : itemRepository
                    .findAllByMenuSection_IdInAndActiveTrueOrderByMenuSection_IdAscDisplayOrderAscProduct_NameAsc(
                        sectionIds
                    );

        Map<UUID, List<MenuItemResponse>>
            itemsBySection =
                new LinkedHashMap<>();

        for (MenuItem item : items) {

            if (
                !item.getProduct()
                    .getOrganization()
                    .getId()
                    .equals(organizationId)
            ) {
                continue;
            }

            if (
                !item.getProduct().isActive()
                || !item.getProduct()
                    .getCategory()
                    .isActive()
            ) {
                continue;
            }

            if (
                item.getVariant() != null
                && !item.getVariant().isActive()
            ) {
                continue;
            }

            itemsBySection
                .computeIfAbsent(
                    item.getMenuSection().getId(),
                    ignored ->
                        new ArrayList<>()
                )
                .add(
                    toItemResponse(item)
                );
        }

        List<MenuSectionResponse>
            sectionResponses =
                sections.stream()
                    .map(section ->
                        new MenuSectionResponse(
                            section.getId(),
                            section.getCode(),
                            section.getName(),
                            section.getMinSelect(),
                            section.getMaxSelect(),
                            section.getDisplayOrder(),
                            section.isActive(),
                            itemsBySection
                                .getOrDefault(
                                    section.getId(),
                                    List.of()
                                )
                        )
                    )
                    .toList();

        return new MenuResponse(
            menu.getId(),
            productId,
            menu.getPricingMode(),
            menu.getDescription(),
            menu.isActive(),
            sectionResponses
        );
    }

    private MenuResponse adminMenuResponse(
        Menu menu
    ) {

        List<MenuSection> sections =
            sectionRepository
                .findAllByMenu_IdOrderByDisplayOrderAscNameAsc(
                    menu.getId()
                );

        List<MenuSectionResponse> responses =
            sections.stream()
                .map(section ->
                    toSectionResponse(
                        section,
                        true
                    )
                )
                .toList();

        return new MenuResponse(
            menu.getId(),
            menu.getProduct().getId(),
            menu.getPricingMode(),
            menu.getDescription(),
            menu.isActive(),
            responses
        );
    }

    private MenuSectionResponse toSectionResponse(
        MenuSection section,
        boolean includeInactiveItems
    ) {

        List<MenuItemResponse> items =
            (
                includeInactiveItems
                    ? itemRepository
                        .findAllByMenuSection_IdInOrderByMenuSection_IdAscDisplayOrderAscProduct_NameAsc(
                            List.of(
                                section.getId()
                            )
                        )
                    : itemRepository
                        .findAllByMenuSection_IdInAndActiveTrueOrderByMenuSection_IdAscDisplayOrderAscProduct_NameAsc(
                            List.of(
                                section.getId()
                            )
                        )
            )
                .stream()
                .map(this::toItemResponse)
                .toList();

        return new MenuSectionResponse(
            section.getId(),
            section.getCode(),
            section.getName(),
            section.getMinSelect(),
            section.getMaxSelect(),
            section.getDisplayOrder(),
            section.isActive(),
            items
        );
    }
    private User authenticatedUser(
        UUID userId
    ) {

        return userRepository
            .findById(userId)
            .orElseThrow(() ->
                new BadCredentialsException(
                    "Authenticated user does not exist."
                )
            );
    }

    private Product productForOrganization(
        UUID productId,
        UUID organizationId
    ) {

        return productRepository
            .findCatalogProduct(
                productId,
                organizationId
            )
            .orElseThrow(() ->
                new CatalogNotFoundException(
                    "Product does not exist."
                )
            );
    }

    private Menu menuForProduct(
        UUID productId
    ) {

        return menuRepository
            .findByProduct_Id(
                productId
            )
            .orElseThrow(() ->
                new CatalogNotFoundException(
                    "Menu does not exist."
                )
            );
    }

    private MenuItemResponse toItemResponse(
        MenuItem item
    ) {

        return new MenuItemResponse(
            item.getId(),
            item.getProduct().getId(),
            item.getProduct().getName(),
            item.getVariant() == null
                ? null
                : item.getVariant().getId(),
            item.getVariant() == null
                ? null
                : item.getVariant().getName(),
            item.getQuantity(),
            item.getPriceDelta(),
            item.isDefaultItem(),
            item.isActive(),
            item.getDisplayOrder()
        );
    }

    private String normalizeNullableText(
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