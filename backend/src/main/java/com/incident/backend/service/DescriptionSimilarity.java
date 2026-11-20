package com.incident.backend.service;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Domain-neutral incident text similarity. Normalizes common forms and broad
 * operational concepts, then compares weighted concept sets with Dice overlap.
 */
@Component
public class DescriptionSimilarity {
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "been", "being", "by", "for", "from", "has", "have",
            "in", "is", "it", "its", "of", "on", "or", "our", "the", "their", "there", "these", "this", "those",
            "to", "was", "we", "were", "with", "while", "across", "every", "several", "some", "any", "all",
            "company", "corporate", "employee", "employees", "user", "users", "staff", "people", "person", "team",
            "unable", "cannot", "can", "not", "can't", "frequently", "repeatedly", "repeated", "often", "extremely",
            "very", "really", "currently", "reasonable", "amount", "due", "using", "use", "experiencing", "experience",
            "application", "applications", "app", "apps", "service", "services", "system", "systems", "device", "devices");

    /* Classification words carry less evidence than symptoms and actions. */
    private static final Set<String> CONTEXT_CONCEPTS = Set.of(
            "database", "network", "payment", "authentication", "server", "cloud", "storage", "security");

    public double compare(String first, String second) {
        Map<String, Double> left = concepts(first);
        Map<String, Double> right = concepts(second);
        if (left.size() < 2 || right.size() < 2) return 0.0;

        double commonWeight = left.entrySet().stream()
                .filter(entry -> right.containsKey(entry.getKey()))
                .mapToDouble(entry -> Math.min(entry.getValue(), right.get(entry.getKey())))
                .sum();
        double totalWeight = left.values().stream().mapToDouble(Double::doubleValue).sum()
                + right.values().stream().mapToDouble(Double::doubleValue).sum();
        return totalWeight == 0.0 ? 0.0 : (2.0 * commonWeight) / totalWeight;
    }

    private Map<String, Double> concepts(String text) {
        if (text == null || text.isBlank()) return Map.of();

        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("wi[- ]?fi", " wireless ")
                .replaceAll("\\b(?:time(?:d|ing)?\\s+out|time[- ]out|timeouts?)\\b", " timeout ")
                .replaceAll("\\b(?:too long|long delays?|slow(?:ly)?|sluggish|lag(?:ging)?|high latency)\\b", " latency ")
                .replaceAll("\\b(?:not going through|cannot complete|can't complete|unable to complete)\\b", " failure ")
                .replaceAll("\\b(?:sign[- ]?in|log[- ]?in|logging in|logged in|authenticate|authenticated|authentication|identity verification)\\b", " authentication ");

        Map<String, Double> result = new HashMap<>();
        Arrays.stream(normalized.split("[^\\p{L}\\p{N}]+"))
                .map(this::canonicalize)
                .filter(token -> token.length() >= 3 && !STOP_WORDS.contains(token))
                .forEach(token -> result.put(token, weight(token)));
        return result;
    }

    private double weight(String concept) {
        return CONTEXT_CONCEPTS.contains(concept) ? 0.35 : 1.0;
    }

    private String canonicalize(String token) {
        return switch (token) {
            case "wifi", "wireless", "connectivity", "connection", "connecting", "connected", "connects" -> "connectivity";
            case "disconnecting", "disconnected", "disconnects", "drops", "dropping", "lose", "loses", "lost",
                    "interruption", "interruptions", "interrupted", "outage", "outages" -> "connectivity_loss";
            case "unstable", "intermittent", "intermittently", "instability" -> "unstable";
            case "frequent", "frequently", "repeated", "repeatedly", "repeats", "repeating" -> "repeat";
            case "users", "user", "employees", "employee", "staff", "people" -> "person";
            case "applications", "apps", "app" -> "application";
            case "reliably", "reliability", "reliable" -> "reliable";
            case "slowly", "slowness", "sluggish", "delays", "delay", "lag", "lagging", "latency" -> "latency";
            case "timeouts", "timedout", "timingout" -> "timeout";
            case "retrieve", "retrieves", "retrieving", "retrieval", "fetch", "fetching", "load", "loads", "loading" -> "retrieve";
            case "records", "record", "information", "infos", "data" -> "data";
            case "queries", "query", "operations", "operation", "sql" -> "operation";
            case "fail", "fails", "failed", "failing", "failure", "failures", "error", "errors", "declined", "reject", "rejected", "denied", "unsuccessful", "incomplete" -> "failure";
            case "transactions", "transaction", "charges", "charge", "purchases", "purchase", "checkout" -> "transaction";
            case "passwords", "password", "credential", "credentials", "passcode", "passcodes" -> "credential";
            case "login", "logins", "signin", "signins", "authentication", "authenticate", "authenticated", "identity" -> "authentication";
            case "unauthorized", "unauthorised", "forbidden", "locked", "lockout" -> "access_denied";
            case "unable", "cannot", "can't" -> "unable";
            default -> stem(token);
        };
    }

    private String stem(String token) {
        if (token.length() > 5 && token.endsWith("ies")) return token.substring(0, token.length() - 3) + "y";
        if (token.length() > 4 && token.endsWith("ing")) {
            String stem = token.substring(0, token.length() - 3);
            if (stem.endsWith("k") || stem.endsWith("v") || stem.endsWith("z")) stem += "e";
            if (stem.length() > 2 && stem.charAt(stem.length() - 1) == stem.charAt(stem.length() - 2)) {
                stem = stem.substring(0, stem.length() - 1);
            }
            return stem;
        }
        if (token.length() > 4 && token.endsWith("ed")) return token.substring(0, token.length() - 2);
        if (token.length() > 4 && token.endsWith("s") && !token.endsWith("ss")) return token.substring(0, token.length() - 1);
        return token;
    }
}
