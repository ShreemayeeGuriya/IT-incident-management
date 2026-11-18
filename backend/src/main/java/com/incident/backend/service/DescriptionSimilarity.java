package com.incident.backend.service;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Computes token-based Dice similarity while ignoring punctuation and common filler words. */
@Component
public class DescriptionSimilarity {
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "at", "be", "for", "from", "has", "in", "is", "it",
            "of", "on", "our", "the", "to", "was", "we", "with");

    public double compare(String first, String second) {
        Set<String> left = tokens(first);
        Set<String> right = tokens(second);
        if (left.isEmpty() || right.isEmpty()) return 0.0;
        long common = left.stream().filter(right::contains).count();
        return (2.0 * common) / (left.size() + right.size());
    }

    private Set<String> tokens(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> token.length() >= 3 && !STOP_WORDS.contains(token))
                .collect(Collectors.toSet());
    }
}
