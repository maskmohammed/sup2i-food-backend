package com.sup2i.food.survey.api;

import com.sup2i.food.catalog.api.dto.PageResponse;
import com.sup2i.food.survey.api.dto.SurveyMutationRequest;
import com.sup2i.food.survey.api.dto.SurveyResponse;
import com.sup2i.food.survey.api.dto.SurveyResultResponse;
import com.sup2i.food.survey.domain.SurveyStatus;
import com.sup2i.food.survey.service.SurveyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin Surveys", description = "Back-office survey management.")
@RestController
@RequestMapping("/api/v1/admin/surveys")
public class AdminSurveyController {

    private final SurveyService service;

    public AdminSurveyController(
        SurveyService service
    ) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('survey.write')")
    public SurveyResponse create(
        @Valid
        @RequestBody
        SurveyMutationRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.create(
            userId(authentication),
            request
        );
    }

    @GetMapping
    @PreAuthorize("hasAuthority('survey.read')")
    public PageResponse<SurveyResponse> list(
        JwtAuthenticationToken authentication,
        @RequestParam(required = false)
        SurveyStatus status,
        @RequestParam(defaultValue = "0")
        int page,
        @RequestParam(defaultValue = "20")
        int size
    ) {
        return service.list(
            userId(authentication),
            status,
            page,
            size
        );
    }

    @GetMapping("/{surveyId}")
    @PreAuthorize("hasAuthority('survey.read')")
    public SurveyResponse get(
        @PathVariable UUID surveyId,
        JwtAuthenticationToken authentication
    ) {
        return service.get(
            userId(authentication),
            surveyId
        );
    }

    @PutMapping("/{surveyId}")
    @PreAuthorize("hasAuthority('survey.write')")
    public SurveyResponse update(
        @PathVariable UUID surveyId,
        @Valid
        @RequestBody
        SurveyMutationRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.update(
            userId(authentication),
            surveyId,
            request
        );
    }

    @PatchMapping("/{surveyId}/publish")
    @PreAuthorize("hasAuthority('survey.write')")
    public SurveyResponse publish(
        @PathVariable UUID surveyId,
        JwtAuthenticationToken authentication
    ) {
        return service.publish(
            userId(authentication),
            surveyId
        );
    }

    @PatchMapping("/{surveyId}/close")
    @PreAuthorize("hasAuthority('survey.write')")
    public SurveyResponse close(
        @PathVariable UUID surveyId,
        JwtAuthenticationToken authentication
    ) {
        return service.close(
            userId(authentication),
            surveyId
        );
    }

    @PatchMapping("/{surveyId}/archive")
    @PreAuthorize("hasAuthority('survey.write')")
    public SurveyResponse archive(
        @PathVariable UUID surveyId,
        JwtAuthenticationToken authentication
    ) {
        return service.archive(
            userId(authentication),
            surveyId
        );
    }

    @GetMapping("/{surveyId}/results")
    @PreAuthorize("hasAuthority('survey.read')")
    public SurveyResultResponse results(
        @PathVariable UUID surveyId,
        JwtAuthenticationToken authentication
    ) {
        return service.results(
            userId(authentication),
            surveyId
        );
    }

    private UUID userId(
        JwtAuthenticationToken authentication
    ) {
        try {
            return UUID.fromString(
                authentication
                    .getToken()
                    .getSubject()
            );
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException(
                "Invalid JWT subject."
            );
        }
    }
}