package com.incident.backend.service;

import com.incident.backend.model.Incident;
import org.springframework.stereotype.Component;

/** Scores candidate incidents using description similarity and matching classification metadata. */
@Component
public class DuplicateIncidentMatcher {
    private static final double DESCRIPTION_WEIGHT = 0.85;
    private static final double CATEGORY_WEIGHT = 0.075;
    private static final double SERVICE_WEIGHT = 0.075;
    // Metadata can refine ranking but text still needs meaningful symptom overlap.
    private static final double MIN_DESCRIPTION_SIMILARITY = 0.35;
    private static final double MIN_TOTAL_SCORE = 0.48;

    private final DescriptionSimilarity descriptionSimilarity;

    public DuplicateIncidentMatcher(DescriptionSimilarity descriptionSimilarity) {
        this.descriptionSimilarity = descriptionSimilarity;
    }

    public double score(String description, Integer categoryId, Integer serviceId, Incident candidate) {
        double textScore = descriptionSimilarity.compare(description, candidate.getDescription());
        if (textScore < MIN_DESCRIPTION_SIMILARITY) return 0.0;

        double score = textScore * DESCRIPTION_WEIGHT;
        if (categoryId.equals(candidate.getCategory().getId())) score += CATEGORY_WEIGHT;
        if (serviceId.equals(candidate.getService().getId())) score += SERVICE_WEIGHT;
        return score >= MIN_TOTAL_SCORE ? score : 0.0;
    }
}
