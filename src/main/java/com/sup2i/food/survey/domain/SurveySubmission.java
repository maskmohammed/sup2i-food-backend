package com.sup2i.food.survey.domain;

import com.sup2i.food.identity.domain.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "survey_responses",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_survey_responses_survey_student",
            columnNames = {"survey_id", "student_id"}
        )
    }
)
public class SurveySubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers", nullable = false, columnDefinition = "jsonb")
    private String answers;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    protected SurveySubmission() {
    }

    public SurveySubmission(
        Survey survey,
        Student student,
        String answers
    ) {
        this.survey = survey;
        this.student = student;
        this.answers = answers == null ? "{}" : answers;
    }

    @PrePersist
    void prePersist() {
        if (submittedAt == null) {
            submittedAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public Survey getSurvey() {
        return survey;
    }

    public Student getStudent() {
        return student;
    }

    public String getAnswers() {
        return answers;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }
}