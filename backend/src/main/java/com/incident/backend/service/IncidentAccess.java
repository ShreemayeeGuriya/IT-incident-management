package com.incident.backend.service;

import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.model.Incident;
import com.incident.backend.model.Role;
import com.incident.backend.model.User;
import com.incident.backend.repository.IncidentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

// One place for the rule "who may see which incident".
@Component
public class IncidentAccess {

    private final IncidentRepository incidentRepository;

    public IncidentAccess(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public static boolean isStaff(User user) {
        return user.getRole() == Role.TECHNICIAN || user.getRole() == Role.ADMIN;
    }

    public Incident findOrThrow(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
    }

    public Incident requireViewable(Long id, User current) {
        Incident incident = findOrThrow(id);
        if (!isStaff(current) && !incident.getRequestor().getId().equals(current.getId())) {
            throw new AccessDeniedException("You can only access your own incidents");
        }
        return incident;
    }
}
