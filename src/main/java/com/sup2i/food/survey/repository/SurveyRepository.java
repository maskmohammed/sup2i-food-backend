package com.sup2i.food.survey.repository;

import com.sup2i.food.survey.domain.Survey;
import com.sup2i.food.survey.domain.SurveyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurveyRepository
    extends JpaRepository<Survey, UUID> {

    Optional<Survey> findByIdAndOrganization_Id(
        UUID id,
        UUID organizationId
    );

    Page<Survey> findAllByOrganization_Id(
        UUID organizationId,
        Pageable pageable
    );

    Page<Survey> findAllByOrganization_IdAndStatus(
        UUID organizationId,
        SurveyStatus status,
        Pageable pageable
    );

    List<Survey> findAllByOrganization_IdAndStatus(
        UUID organizationId,
        SurveyStatus status
    );
}