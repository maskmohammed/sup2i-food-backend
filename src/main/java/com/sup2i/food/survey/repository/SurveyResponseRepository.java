package com.sup2i.food.survey.repository;

import com.sup2i.food.survey.domain.SurveySubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SurveyResponseRepository
    extends JpaRepository<SurveySubmission, UUID> {

    boolean existsBySurvey_IdAndStudent_Id(
        UUID surveyId,
        UUID studentId
    );

    List<SurveySubmission> findAllBySurvey_Id(
        UUID surveyId
    );
}