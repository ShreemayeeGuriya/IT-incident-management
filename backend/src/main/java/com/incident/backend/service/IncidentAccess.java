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
        return incidentRepository.findById(Math.toIntExact(id))
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
    }

    public Incident findForUpdate(Long id) {
        return incidentRepository.findByIdForUpdate(Math.toIntExact(id))
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
    }

    public Incident requireViewable(Long id, User current) {
        Incident incident = findOrThrow(id);
        assertViewable(incident, current);
        return incident;
    }

    public Incident requireViewableForUpdate(Long id, User current) {
        Incident incident = findForUpdate(id);
        assertViewable(incident, current);
        return incident;
    }

    private void assertViewable(Incident incident, User current) {
        if (!isStaff(current) && !incident.getReporter().getId().equals(current.getId())) {
            throw new AccessDeniedException("You can only access your own incidents");
        }
    }
}
