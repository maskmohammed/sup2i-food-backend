package com.sup2i.food.survey.domain;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "survey_questions")
public class SurveyQuestion {

    private static final com.fasterxml.jackson.databind.ObjectMapper
        MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Column(name = "question", nullable = false, columnDefinition = "text")
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private QuestionType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "jsonb")
    private String options = "[]";

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "required", nullable = false)
    private boolean required = false;

    protected SurveyQuestion() {
    }

    public SurveyQuestion(
        String question,
        QuestionType type,
        List<String> options,
        int displayOrder,
        boolean required
    ) {
        this.question = question;
        this.type = type;
        this.options = options == null ? "[]" : toJson(options);
        this.displayOrder = displayOrder;
        this.required = required;
    }

    public UUID getId() {
        return id;
    }

    public Survey getSurvey() {
        return survey;
    }

    public void setSurvey(Survey survey) {
        this.survey = survey;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public QuestionType getType() {
        return type;
    }

    public void setType(QuestionType type) {
        this.type = type;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(List<String> optionValues) {
        this.options = optionValues == null ? "[]" : toJson(optionValues);
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public List<String> optionValues() {
        if (
            options == null
                || options.isBlank()
        ) {
            return List.of();
        }

        try {
            return MAPPER.readValue(
                options,
                new TypeReference<List<String>>() {
                }
            );
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static String toJson(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception exception) {
            return "[]";
        }
    }
}