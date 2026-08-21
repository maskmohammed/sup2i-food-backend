package com.sup2i.food.catalog.service;

import com.sup2i.food.catalog.api.dto.ProductLocationSettingResponse;
import com.sup2i.food.catalog.api.dto.UpsertProductLocationSettingRequest;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductLocationSetting;
import com.sup2i.food.catalog.exception.CatalogConflictException;
import com.sup2i.food.catalog.exception.CatalogNotFoundException;
import com.sup2i.food.catalog.repository.ProductLocationSettingRepository;
import com.sup2i.food.catalog.repository.ProductRepository;
import com.sup2i.food.identity.domain.User;
import com.sup2i.food.identity.repository.UserRepository;
import com.sup2i.food.organization.domain.Location;
import com.sup2i.food.organization.repository.LocationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class CatalogLocationService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;

    private final ProductLocationSettingRepository
        settingRepository;

    public CatalogLocationService(
        UserRepository userRepository,
        ProductRepository productRepository,
        LocationRepository locationRepository,
        ProductLocationSettingRepository settingRepository
    ) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.settingRepository = settingRepository;
    }

    @Transactional
    public ProductLocationSettingResponse upsert(
        UUID actorId,
        UUID productId,
        UUID locationId,
        UpsertProductLocationSettingRequest request
    ) {

        User actor =
            authenticatedUser(actorId);

        UUID organizationId =
            actor.getOrganization().getId();

        Product product =
            productRepository
                .findCatalogProduct(
                    productId,
                    organizationId
                )
                .orElseThrow(() ->
                    new CatalogNotFoundException(
                        "Product does not exist."
                    )
                );

        Location location =
            locationRepository
                .findByIdAndCampus_Organization_Id(
                    locationId,
                    organizationId
                )
                .orElseThrow(() ->
                    new CatalogNotFoundException(
                        "Location does not exist."
                    )
                );

        ProductLocationSetting setting =
            settingRepository
                .findByProduct_IdAndLocation_Id(
                    productId,
                    locationId
                )
                .orElseGet(() ->
                    new ProductLocationSetting(
                        product,
                        location
                    )
                );

        setting.setEnabled(
            request.enabled() == null
                || request.enabled()
        );

        setting.setAllowedDays(
            toShortArray(
                request.allowedDays()
            )
        );

        setting.setAvailableFromTime(
            request.availableFromTime()
        );

        setting.setAvailableToTime(
            request.availableToTime()
        );

        setting.setPreparationMinutes(
            request.preparationMinutes()
        );

        try {
            setting =
                settingRepository
                    .saveAndFlush(setting);
        } catch (
            DataIntegrityViolationException exception
        ) {
            throw new CatalogConflictException(
                "Product location setting conflicts with an existing resource."
            );
        }

        return toResponse(setting);
    }

    @Transactional(readOnly = true)
    public List<ProductLocationSettingResponse> findAll(
        UUID userId,
        UUID productId
    ) {

        User user =
            authenticatedUser(userId);

        UUID organizationId =
            user.getOrganization().getId();

        productRepository
            .findCatalogProduct(
                productId,
                organizationId
            )
            .orElseThrow(() ->
                new CatalogNotFoundException(
                    "Product does not exist."
                )
            );

        return settingRepository
            .findAllByProduct_IdOrderByLocation_NameAsc(
                productId
            )
            .stream()
            .map(this::toResponse)
            .toList();
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

    private Short[] toShortArray(
        List<Integer> days
    ) {

        if (days == null) {
            return null;
        }

        return days.stream()
            .sorted()
            .map(Integer::shortValue)
            .toArray(Short[]::new);
    }

    private ProductLocationSettingResponse toResponse(
        ProductLocationSetting setting
    ) {

        List<Integer> days =
            setting.getAllowedDays() == null
                ? null
                : Arrays.stream(
                        setting.getAllowedDays()
                    )
                    .map(Short::intValue)
                    .toList();

        return new ProductLocationSettingResponse(
            setting.getId(),
            setting.getLocation().getId(),
            setting.getLocation().getName(),
            setting.isEnabled(),
            days,
            setting.getAvailableFromTime(),
            setting.getAvailableToTime(),
            setting.getPreparationMinutes()
        );
    }
}