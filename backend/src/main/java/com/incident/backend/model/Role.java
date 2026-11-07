package com.incident.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
    EMPLOYEE, TECHNICIAN, ADMIN;

    /** Accepts the legacy API role while storing the schema's EMPLOYEE value. */
    @JsonCreator
    public static Role fromApiValue(String value) {
        if (value == null) return null;
        return switch (value.trim().toUpperCase()) {
            case "USER", "EMPLOYEE" -> EMPLOYEE;
            case "TECHNICIAN" -> TECHNICIAN;
            case "ADMIN" -> ADMIN;
            default -> throw new IllegalArgumentException("Unknown role: " + value);
        };
    }

    /** Keeps the existing frontend's USER label for employee accounts. */
    @JsonValue
    public String toApiValue() {
        return this == EMPLOYEE ? "USER" : name();
    }
}
