package com.incident.backend.service.email;

public class SimulatedEmailException extends RuntimeException {
    public SimulatedEmailException(String message) {
        super(message);
    }
}
