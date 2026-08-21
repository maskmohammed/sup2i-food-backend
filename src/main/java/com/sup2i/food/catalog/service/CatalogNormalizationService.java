package com.sup2i.food.catalog.service;

import com.sup2i.food.catalog.exception.CatalogValidationException;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class CatalogNormalizationService {

    public String normalizeSlug(
        String requestedSlug,
        String name
    ) {
        String source =
            requestedSlug == null
                || requestedSlug.isBlank()
                    ? name
                    : requestedSlug;

        String normalized =
            Normalizer.normalize(
                source.trim(),
                Normalizer.Form.NFKD
            );

        normalized =
            normalized.replaceAll(
                "\\p{M}+",
                ""
            );

        normalized =
            normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll(
                    "[^\\p{L}\\p{N}]+",
                    "-"
                )
                .replaceAll(
                    "^-+|-+$",
                    ""
                );

        if (normalized.isBlank()) {
            throw new CatalogValidationException(
                "Category slug cannot be empty."
            );
        }

        if (normalized.length() > 140) {
            normalized =
                normalized.substring(
                    0,
                    140
                );

            normalized =
                normalized.replaceAll(
                    "-+$",
                    ""
                );
        }

        return normalized;
    }

    public String normalizeSku(
        String sku
    ) {
        return sku
            .trim()
            .toUpperCase(
                Locale.ROOT
            );
    }
}