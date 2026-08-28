package com.sup2i.food.review.api.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sup2i.food.review.domain.Review;

import java.util.List;

final class ReviewJson {

    private static final com.fasterxml.jackson.databind.ObjectMapper
        MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private ReviewJson() {
    }

    static List<String> photos(Review review) {
        if (
            review.getPhotos() == null
                || review.getPhotos().isBlank()
        ) {
            return List.of();
        }

        try {
            return MAPPER.readValue(
                review.getPhotos(),
                new TypeReference<List<String>>() {
                }
            );
        } catch (Exception exception) {
            return List.of();
        }
    }
}