package com.sup2i.food.survey.api;

import com.sup2i.food.survey.api.dto.SurveyResponse;
import com.sup2i.food.survey.api.dto.SurveySubmissionResponse;
import com.sup2i.food.survey.api.dto.SubmitSurveyRequest;
import com.sup2i.food.survey.service.SurveyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Surveys", description = "Student surveys: list open surveys, answer a survey.")
@RestController
@RequestMapping("/api/v1/surveys")
public class SurveyController {

    private final SurveyService service;

    public SurveyController(
        SurveyService service
    ) {
        this.service = service;
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public List<SurveyResponse> active(
        JwtAuthenticationToken authentication
    ) {
        return service.activeForStudent(
            userId(authentication)
        );
    }

    @PostMapping("/{surveyId}/respond")
    @PreAuthorize("isAuthenticated()")
    public SurveySubmissionResponse respond(
        @PathVariable UUID surveyId,
        @Valid
        @RequestBody
        SubmitSurveyRequest request,
        JwtAuthenticationToken authentication
    ) {
        return service.respond(
            userId(authentication),
            surveyId,
            request
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